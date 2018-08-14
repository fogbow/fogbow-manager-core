package org.fogbowcloud.manager.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.fogbowcloud.manager.core.constants.ConfigurationConstants;
import org.fogbowcloud.manager.core.exceptions.*;
import org.fogbowcloud.manager.core.cloudconnector.CloudConnectorFactory;
import org.fogbowcloud.manager.core.cloudconnector.CloudConnector;
import org.fogbowcloud.manager.core.models.InstanceStatus;
import org.fogbowcloud.manager.core.models.ResourceType;
import org.fogbowcloud.manager.core.models.instances.InstanceState;
import org.fogbowcloud.manager.core.models.orders.ComputeOrder;
import org.fogbowcloud.manager.core.models.quotas.allocation.Allocation;
import org.fogbowcloud.manager.core.models.quotas.allocation.ComputeAllocation;
import org.fogbowcloud.manager.core.models.orders.Order;
import org.fogbowcloud.manager.core.models.orders.OrderState;
import org.fogbowcloud.manager.core.models.instances.Instance;
import org.fogbowcloud.manager.core.models.tokens.FederationUserToken;
import org.apache.log4j.Logger;

public class OrderController {

    private static final Logger LOGGER = Logger.getLogger(OrderController.class);

    private final SharedOrderHolders orderHolders;

    public OrderController() {
        this.orderHolders = SharedOrderHolders.getInstance();
    }

    public void setEmptyFieldsAndActivateOrder(Order order, FederationUserToken federationUserToken) throws UnexpectedException {
        // Set order fields that have not been provided by the requester
        order.setId(UUID.randomUUID().toString());
        order.setFederationUserToken(federationUserToken);
        String localMemberId = PropertiesHolder.getInstance().getProperty(ConfigurationConstants.LOCAL_MEMBER_ID);
        order.setRequestingMember(localMemberId);
        if (order.getProvidingMember() == null) {
            order.setProvidingMember(localMemberId);
        }
        // Set an initial state for the instance that is yet to be created in the cloud
        order.setCachedInstanceState(InstanceState.DISPATCHED);
        // Add order to the poll of active orders and to the OPEN linked list
        OrderStateTransitioner.activateOrder(order);
    }

    public Order getOrder(String orderId) throws InstanceNotFoundException {
        Order requestedOrder = this.orderHolders.getActiveOrdersMap().get(orderId);
        String msg = (requestedOrder == null ? "null" : requestedOrder.toString());
        if (requestedOrder == null) {
            throw new InstanceNotFoundException();
        }
        return requestedOrder;
    }

    public void deleteOrder(String orderId) throws InstanceNotFoundException, UnexpectedException,
            InvalidParameterException {
        if (orderId == null) throw new InvalidParameterException("No instance id informed");
        Order order = getOrder(orderId);
        synchronized (order) {
            OrderState orderState = order.getOrderState();
            if (!orderState.equals(OrderState.CLOSED)) {
                OrderStateTransitioner.transition(order, OrderState.CLOSED);
           } else {
                String message = "Order [" + order.getId() + "] is already in the closed state";
                LOGGER.error(message);
                throw new InstanceNotFoundException(message);
            }
        }
    }

    public Instance getResourceInstance(String orderId) throws Exception {
        if (orderId == null) throw new InvalidParameterException("No instance id informed");
        Order order = getOrder(orderId);
        synchronized (order) {
            CloudConnector cloudConnector =
                    CloudConnectorFactory.getInstance().getCloudConnector(order.getProvidingMember());
            return cloudConnector.getInstance(order);
        }
    }

    public Allocation getUserAllocation(String memberId, FederationUserToken federationUserToken, ResourceType resourceType)
            throws UnexpectedException {

        Collection<Order> orders = this.orderHolders.getActiveOrdersMap().values();

        List<Order> filteredOrders = orders.stream()
                .filter(order -> order.getType().equals(resourceType))
                .filter(order -> order.getOrderState().equals(OrderState.FULFILLED))
                .filter(order -> order.isProviderLocal(memberId))
                .filter(order -> order.getFederationUserToken().equals(federationUserToken))
                .collect(Collectors.toList());

        switch (resourceType) {
            case COMPUTE:
                List<ComputeOrder> computeOrders = new ArrayList<>();
                for (Order order : filteredOrders) {
                    computeOrders.add((ComputeOrder) order);
                }
                return getUserComputeAllocation(computeOrders);
            default:
                throw new UnexpectedException("Not yet implemented.");
        }
    }

    public List<InstanceStatus> getInstancesStatus(FederationUserToken federationUserToken, ResourceType resourceType) {
        List<InstanceStatus> instanceStatusList = new ArrayList<>();
        List<Order> allOrders = getAllOrders(federationUserToken, resourceType);
        for (Order order : allOrders) {
            // The state of the instance can be inferred from the state of the order
            InstanceStatus instanceStatus = new InstanceStatus(order.getId(), order.getProvidingMember(),
                    order.getCachedInstanceState());
            instanceStatusList.add(instanceStatus);
            LOGGER.debug("getInstancesStatus: orderId " + order.getId() + " provider " + order.getProvidingMember() + " status " + order.getCachedInstanceState());
        }
        return instanceStatusList;
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

    private List<Order> getAllOrders(FederationUserToken federationUserToken, ResourceType resourceType) {
        Collection<Order> orders = this.orderHolders.getActiveOrdersMap().values();

        // Filter all orders of resourceType from federationUserToken that are not closed (closed orders have been deleted by
        // the user and should not be seen; they will disappear from the system as soon as the closedProcessor thread
        // process them).
        List<Order> requestedOrders =
                orders.stream()
                        .filter(order -> order.getType().equals(resourceType))
                        .filter(order -> order.getFederationUserToken().equals(federationUserToken))
                        .filter(order -> !order.getOrderState().equals(OrderState.CLOSED))
                        .collect(Collectors.toList());
        return requestedOrders;
    }
}

