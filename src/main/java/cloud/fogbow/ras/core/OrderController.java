package cloud.fogbow.ras.core;

import cloud.fogbow.common.exceptions.*;
import cloud.fogbow.common.models.SystemUser;
import cloud.fogbow.common.models.linkedlists.ChainedList;
import cloud.fogbow.ras.constants.ConfigurationPropertyKeys;
import cloud.fogbow.ras.constants.Messages;
import cloud.fogbow.ras.core.cloudconnector.CloudConnector;
import cloud.fogbow.ras.core.cloudconnector.CloudConnectorFactory;
import cloud.fogbow.ras.core.models.Operation;
import cloud.fogbow.ras.core.models.ResourceType;
import cloud.fogbow.ras.core.models.orders.*;
import cloud.fogbow.ras.api.http.response.InstanceStatus;
import cloud.fogbow.ras.api.http.response.Instance;
import cloud.fogbow.ras.api.http.response.quotas.allocation.Allocation;
import cloud.fogbow.ras.api.http.response.quotas.allocation.ComputeAllocation;
import org.apache.log4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class OrderController {
    private static final Logger LOGGER = Logger.getLogger(OrderController.class);

    private final SharedOrderHolders orderHolders;
    private Map<String, List<String>> orderDependencies;
    private String localMemberId;

    public OrderController() {
        this.orderHolders = SharedOrderHolders.getInstance();
        this.orderDependencies = new ConcurrentHashMap<>();
        this.localMemberId = PropertiesHolder.getInstance().getProperty(ConfigurationPropertyKeys.LOCAL_MEMBER_ID_KEY);
    }

    public Order getOrder(String orderId) throws InstanceNotFoundException {
        Order requestedOrder = this.orderHolders.getActiveOrdersMap().get(orderId);
        if (requestedOrder == null) {
            throw new InstanceNotFoundException();
        }
        return requestedOrder;
    }

    public void activateOrder(Order order) throws FogbowException {
        LOGGER.info(Messages.Info.ACTIVATING_NEW_REQUEST);

        if (order == null) {
            throw new UnexpectedException(Messages.Exception.UNABLE_TO_PROCESS_EMPTY_REQUEST);
        }

        // One might think that the synchronized block is not really needed, since the order should not exist
        // until now, and no other thread could be manipulating it at this time; although highly unlikely, it
        // is possible that ids randomly assigned clash, or that a malicious user submit requests with random
        // generated ids that may clash with the id assigned to this order. The cost of being extra safe is low.
        synchronized (order) {
            SharedOrderHolders sharedOrderHolders = SharedOrderHolders.getInstance();
            Map<String, Order> activeOrdersMap = sharedOrderHolders.getActiveOrdersMap();
            ChainedList<Order> openOrdersList = sharedOrderHolders.getOpenOrdersList();

            String orderId = order.getId();
            if (activeOrdersMap.containsKey(orderId)) {
                String message = String.format(Messages.Exception.REQUEST_ID_ALREADY_ACTIVATED, orderId);
                throw new UnexpectedException(message);
            }

            order.setOrderState(OrderState.OPEN);
            activeOrdersMap.put(orderId, order);
            openOrdersList.addItem(order);
            // Sometimes an order depends on other orders (ex. an attachment depends on a volume and a compute).
            // We need to keep this information, so to disallow the deletion of an order on which another order
            // depends (ex. we should not allow the deletion of a volume, for which there is an active attachment),
            // but the information needs only to be kept at the member that received the create request through its
            // REST API.
            if (order.isRequesterLocal(this.localMemberId)) {
                this.updateOrderDependencies(order, Operation.CREATE);
            }
        }
    }

    public void deactivateOrder(Order order) throws UnexpectedException {
        SharedOrderHolders sharedOrderHolders = SharedOrderHolders.getInstance();
        Map<String, Order> activeOrdersMap = sharedOrderHolders.getActiveOrdersMap();
        ChainedList<Order> closedOrders = sharedOrderHolders.getClosedOrdersList();

        synchronized (order) {
            if (activeOrdersMap.containsKey(order.getId())) {
                activeOrdersMap.remove(order.getId());
            } else {
                String message = String.format(Messages.Exception.UNABLE_TO_REMOVE_INACTIVE_REQUEST, order.getId());
                throw new UnexpectedException(message);
            }
            closedOrders.removeItem(order);
            order.setInstanceId(null);
            order.setOrderState(OrderState.DEACTIVATED);
        }
    }

    public void deleteOrder(Order order) throws FogbowException {
        if (order == null)
            throw new UnexpectedException(Messages.Exception.CORRUPTED_INSTANCE);

        // This code may be executed by two different members for the same logical order. This is the case when the
        // provider member is remote. In this case, some parts of the code should be executed only at the member that
        // receives the request through its REST API, while other parts should be executed only by the member that
        // is providing resources to the order.
        synchronized (order) {
            OrderState orderState = order.getOrderState();
            if (!orderState.equals(OrderState.CLOSED)) {
                // Sometimes an order depends on other orders (ex. an attachment depends on a volume and a compute).
                // We need to verify whether another order depends on this order, and if this is the case, throw a
                // DependencyDetectedException. Only the member that is receiving the delete request through its
                // REST API needs to check order dependencies.
                if (order.isRequesterLocal(this.localMemberId)) {
                    checkOrderDependencies(order.getId());
                }
                CloudConnector cloudConnector = getCloudConnector(order);
                try {
                    // When the order is remote, there is no local instance; also, a previous call to deleteOrder might
                    // have failed after the instance had already been removed from the cloud. In both cases, the
                    // instanceId will be null, and there is no need to call deleteInstance.
                    if ((order.isProviderLocal(this.localMemberId) && order.getInstanceId() != null) ||
                        order.isProviderRemote(this.localMemberId)) {
                        cloudConnector.deleteInstance(order);
                        order.setInstanceId(null);
                    }
                } catch (FogbowException e) {
                    LOGGER.error(String.format(Messages.Error.ERROR_MESSAGE, order.getId()), e);
                    throw e;
                }
                // Only the member that is providing the order should update the order's state. Thus, when the
                // provider is remote, the remote order is updated here, while the local order  state is updated
                // when the remote provider sends a message to the local member.
                if (order.isProviderLocal(this.localMemberId)) {
                    OrderStateTransitioner.transition(order, OrderState.CLOSED);
                 }
                // Remove any references that related dependencies of other orders with the order that has
                // just been deleted. Only the member that is receiving the delete request through its
                // REST API needs to update order dependencies.
                if (order.isRequesterLocal(this.localMemberId)) {
                    updateOrderDependencies(order, Operation.DELETE);
                }

            } else {
                String message = String.format(Messages.Error.REQUEST_ALREADY_CLOSED, order.getId());
                throw new InstanceNotFoundException(message);
            }
        }
    }

    public Instance getResourceInstance(Order order) throws FogbowException {
        if (order == null)
        	throw new UnexpectedException(Messages.Exception.CORRUPTED_INSTANCE);

        synchronized (order) {
            CloudConnector cloudConnector = getCloudConnector(order);
            Instance instance = cloudConnector.getInstance(order);
            return instance;
        }
    }

    public Allocation getUserAllocation(String memberId, SystemUser systemUser, ResourceType resourceType)
            throws UnexpectedException {
        Collection<Order> orders = this.orderHolders.getActiveOrdersMap().values();

        List<Order> filteredOrders = orders.stream()
                .filter(order -> order.getType().equals(resourceType))
                .filter(order -> order.getOrderState().equals(OrderState.FULFILLED))
                .filter(order -> order.isProviderLocal(memberId))
                .filter(order -> order.getSystemUser().equals(systemUser))
                .collect(Collectors.toList());

        switch (resourceType) {
            case COMPUTE:
                List<ComputeOrder> computeOrders = new ArrayList<>();
                for (Order order : filteredOrders) {
                    computeOrders.add((ComputeOrder) order);
                }
                return getUserComputeAllocation(computeOrders);
            default:
                throw new UnexpectedException(Messages.Exception.RESOURCE_TYPE_NOT_IMPLEMENTED);
        }
    }

	public List<InstanceStatus> getInstancesStatus(SystemUser systemUser, ResourceType resourceType) throws UnexpectedException {
		List<InstanceStatus> instanceStatusList = new ArrayList<>();
		List<Order> allOrders = getAllOrders(systemUser, resourceType);

		for (Order order : allOrders) {
			String name = null;

			switch (resourceType) {
			case COMPUTE:
				name = ((ComputeOrder) order).getName();
				break;
			case VOLUME:
				name = ((VolumeOrder) order).getName();
				break;
			case NETWORK:
				name = ((NetworkOrder) order).getName();
				break;
			default:
				break;
			}

			// The state of the instance can be inferred from the state of the order. This is not the cloud-dependent
            // state of the instance (that can be consulted by issuing a GET request on a particular instance), but a
            // more generic (and cloud-independent) indication of the instance's state.
			InstanceStatus instanceStatus = new InstanceStatus(
					order.getId(), 
					name, 
					order.getProvider(),
					order.getCloudName(), 
					InstanceStatus.mapInstanceStateFromOrderState(order.getOrderState()));
			
			instanceStatusList.add(instanceStatus);
		}

		return instanceStatusList;
	}

    protected CloudConnector getCloudConnector(Order order) {
        CloudConnector provider = null;
        String localMemberId = PropertiesHolder.getInstance().getProperty(ConfigurationPropertyKeys.LOCAL_MEMBER_ID_KEY);
        
        if (order.isProviderLocal(localMemberId)) {
            provider = CloudConnectorFactory.getInstance().getCloudConnector(localMemberId, order.getCloudName());
        } else {
            if (order.getOrderState().equals(OrderState.OPEN) ||
                    order.getOrderState().equals(OrderState.FAILED_ON_REQUEST)) {
            
            	// This is an order for a remote provider that has never been received by that provider.
            	// Thus, there is no need to send a delete message via a RemoteCloudConnector, and it is 
            	// only necessary to call deleteInstance in the local member.
                provider = CloudConnectorFactory.getInstance().getCloudConnector(localMemberId, order.getCloudName());
            
            } else {
                provider = CloudConnectorFactory.getInstance().getCloudConnector(order.getProvider(), order.getCloudName());
            }
        }
        return provider;
    }

    private ComputeAllocation getUserComputeAllocation(Collection<ComputeOrder> computeOrders) {
        int vCPU = 0;
        int ram = 0;
        int instances = 0;

        for (ComputeOrder order : computeOrders) {
            ComputeAllocation actualAllocation = order.getActualAllocation();
            vCPU += actualAllocation.getvCPU();
            ram += actualAllocation.getRam();
            instances += actualAllocation.getInstances();
        }

        return new ComputeAllocation(vCPU, ram, instances);
    }

	private List<Order> getAllOrders(SystemUser systemUser, ResourceType resourceType) {
		Collection<Order> orders = this.orderHolders.getActiveOrdersMap().values();

		// Filter all orders of resourceType from the user systemUser that are not
		// closed (closed orders have been deleted by the user and should not be seen;
		// they will disappear from the system as soon as the closedProcessor thread
		// process them).
		List<Order> requestedOrders = orders.stream()
				.filter(order -> order.getType().equals(resourceType))
				.filter(order -> order.getSystemUser().equals(systemUser))
				.filter(order -> !order.getOrderState().equals(OrderState.CLOSED)).collect(Collectors.toList());

		return requestedOrders;
	}

	private void updateOrderDependencies(Order order, Operation operation) throws FogbowException {
        List<String> dependentOrderIds = new LinkedList<>();

        switch (order.getType()) {
            case ATTACHMENT:
                AttachmentOrder attachmentOrder = (AttachmentOrder) order;
                dependentOrderIds.add(attachmentOrder.getComputeOrderId());
                dependentOrderIds.add(attachmentOrder.getVolumeOrderId());
                break;
            case COMPUTE:
                ComputeOrder computeOrder = (ComputeOrder) order;
                dependentOrderIds.addAll(computeOrder.getNetworkOrderIds());
                break;
            case PUBLIC_IP:
                PublicIpOrder publicIpOrder = (PublicIpOrder) order;
                dependentOrderIds.add(publicIpOrder.getComputeOrderId());
                break;
            default:
                // Dependencies apply only to attachment, compute and public IP orders for now.
                return;
        }

        switch (operation) {
            case CREATE:
                addOrderDependency(order.getId(), dependentOrderIds);
                break;
            case DELETE:
                deleteOrderDependency(order.getId(), dependentOrderIds);
                break;
                default:
                    throw new UnexpectedException(String.format(Messages.Exception.UNEXPECTED_OPERATION_S, operation));
        }
    }

    private void deleteOrderDependency(String orderId, List<String> dependentOrderIds) throws UnexpectedException {
        for (String dependentOrderId : dependentOrderIds) {
            if (this.orderDependencies.containsKey(dependentOrderId)) {
                List<String> currentList = this.orderDependencies.get(dependentOrderId);
                if (currentList.remove(orderId)) {
                    this.orderDependencies.put(dependentOrderId, currentList);
                } else {
                    LOGGER.error(String.format(Messages.Error.COULD_NOT_FIND_DEPENDENCY_S_S, dependentOrderId, orderId));
                }
            } else {
                LOGGER.error(String.format(Messages.Error.COULD_NOT_FIND_DEPENDENCY_S_S, dependentOrderId, orderId));
            }
        }
    }

    private void addOrderDependency(String orderId, List<String> dependentOrderIds) {
        for (String dependentOrderId : dependentOrderIds) {
            if (this.orderDependencies.containsKey(dependentOrderId)) {
                List<String> currentList = this.orderDependencies.get(dependentOrderId);
                currentList.add(orderId);
                this.orderDependencies.put(dependentOrderId, currentList);
            } else {
                List<String> currentList = new LinkedList<>();
                currentList.add(orderId);
                this.orderDependencies.put(dependentOrderId, currentList);
            }
        }
    }

    private void checkOrderDependencies(String orderId) throws DependencyDetectedException {
        if (this.orderDependencies.containsKey(orderId) &&
            !this.orderDependencies.get(orderId).isEmpty()) {
                throw new DependencyDetectedException(String.format(Messages.Exception.DEPENDENCY_DETECTED, orderId,
                                          this.orderDependencies.get(orderId)));
        }
    }
}

