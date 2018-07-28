package org.fogbowcloud.manager.core;

import org.fogbowcloud.manager.core.cloudconnector.CloudConnectorFactory;
import org.fogbowcloud.manager.core.cloudconnector.LocalCloudConnector;
import org.fogbowcloud.manager.core.datastore.DatabaseManager;
import org.fogbowcloud.manager.core.exceptions.*;
import org.fogbowcloud.manager.core.models.instances.ComputeInstance;
import org.fogbowcloud.manager.core.models.instances.Instance;
import org.fogbowcloud.manager.core.models.instances.InstanceState;
import org.fogbowcloud.manager.core.models.instances.InstanceType;
import org.fogbowcloud.manager.core.models.linkedlists.ChainedList;
import org.fogbowcloud.manager.core.models.linkedlists.SynchronizedDoublyLinkedList;
import org.fogbowcloud.manager.core.models.orders.*;
import org.fogbowcloud.manager.core.models.quotas.allocation.ComputeAllocation;
import org.fogbowcloud.manager.core.models.tokens.FederationUser;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.BDDMockito;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(PowerMockRunner.class)
@PrepareForTest({DatabaseManager.class, CloudConnectorFactory.class})
public class OrderControllerTest extends BaseUnitTests {

    private OrderController ordersController;

    private Map<String, Order> activeOrdersMap;
    private ChainedList openOrdersList;
    private ChainedList pendingOrdersList;
    private ChainedList spawningOrdersList;
    private ChainedList fulfilledOrdersList;
    private ChainedList failedOrdersList;
    private ChainedList closedOrdersList;
    private String localMember = BaseUnitTests.LOCAL_MEMBER_ID;

    @Before
    public void setUp() {
        HomeDir.getInstance().setPath("src/test/resources/private");

        // mocking database to return empty instances of SynchronizedDoublyLinkedList.
        DatabaseManager databaseManager = Mockito.mock(DatabaseManager.class);
        Mockito.when(databaseManager.readActiveOrders(OrderState.OPEN)).thenReturn(new SynchronizedDoublyLinkedList());
        Mockito.when(databaseManager.readActiveOrders(OrderState.SPAWNING)).thenReturn(new SynchronizedDoublyLinkedList());
        Mockito.when(databaseManager.readActiveOrders(OrderState.FAILED)).thenReturn(new SynchronizedDoublyLinkedList());
        Mockito.when(databaseManager.readActiveOrders(OrderState.FULFILLED)).thenReturn(new SynchronizedDoublyLinkedList());
        Mockito.when(databaseManager.readActiveOrders(OrderState.PENDING)).thenReturn(new SynchronizedDoublyLinkedList());
        Mockito.when(databaseManager.readActiveOrders(OrderState.CLOSED)).thenReturn(new SynchronizedDoublyLinkedList());

        Mockito.doNothing().when(databaseManager).add(Mockito.any(Order.class));
        Mockito.doNothing().when(databaseManager).update(Mockito.any(Order.class));

        PowerMockito.mockStatic(DatabaseManager.class);
        BDDMockito.given(DatabaseManager.getInstance()).willReturn(databaseManager);

        this.ordersController = new OrderController();

        SharedOrderHolders sharedOrderHolders = SharedOrderHolders.getInstance();

        // setting up the attributes.
        this.activeOrdersMap = sharedOrderHolders.getActiveOrdersMap();
        this.openOrdersList = sharedOrderHolders.getOpenOrdersList();
        this.pendingOrdersList = sharedOrderHolders.getPendingOrdersList();
        this.spawningOrdersList = sharedOrderHolders.getSpawningOrdersList();
        this.fulfilledOrdersList = sharedOrderHolders.getFulfilledOrdersList();
        this.failedOrdersList = sharedOrderHolders.getFailedOrdersList();
        this.closedOrdersList = sharedOrderHolders.getClosedOrdersList();
    }

    // test case: There is no matching method in the 'OrdersController' class.
    @Test(expected = UnexpectedException.class)
    public void testFailedNewOrderRequestOrderIsNull() throws UnexpectedException {
    	// exercise
        Order order = null;
    	OrderStateTransitioner.activateOrder(order);
    }

    // test case: A closed order cannot be deleted, so it must raise a FogbowManagerException.
    @Test(expected = FogbowManagerException.class)
    public void testDeleteOrderStateClosed() throws UnexpectedException, InvalidParameterException,
            InstanceNotFoundException {
        // exercise
        String orderId = getComputeOrderCreationId(OrderState.CLOSED);
        ComputeOrder computeOrder = (ComputeOrder) this.activeOrdersMap.get(orderId);

        this.ordersController.deleteOrder(computeOrder);
    }

    // test case: Checks if getAllOrders() returns exactly the same orders that
    // were added on the lists.
    @Test
    public void testGetAllOrders() throws InvalidParameterException {
        // set up
        Map<String, String> attributes = new HashMap<String, String>();
        attributes.put(FederationUser.MANDATORY_NAME_ATTRIBUTE, "fake-name");
        FederationUser federationUser = new FederationUser("fake-id", attributes);
        ComputeOrder computeOrder = new ComputeOrder();
        computeOrder.setFederationUser(federationUser);
        computeOrder.setRequestingMember(this.localMember);
        computeOrder.setProvidingMember(this.localMember);
        computeOrder.setOrderState(OrderState.OPEN);

        ComputeOrder computeOrder2 = new ComputeOrder();
        computeOrder2.setFederationUser(federationUser);
        computeOrder2.setRequestingMember(this.localMember);
        computeOrder2.setProvidingMember(this.localMember);
        computeOrder2.setOrderState(OrderState.FULFILLED);

        this.activeOrdersMap.put(computeOrder.getId(), computeOrder);
        this.openOrdersList.addItem(computeOrder);

        this.activeOrdersMap.put(computeOrder2.getId(), computeOrder2);
        this.fulfilledOrdersList.addItem(computeOrder2);

        // exercise
        List<Order> orders = this.ordersController.getAllOrders(federationUser, InstanceType.COMPUTE);

        // verify
        Assert.assertTrue(orders.contains(computeOrder));
        Assert.assertTrue(orders.contains(computeOrder2));
    }

    // test case: Checks if getOrder() returns exactly the same order that
    // were added on the list.
    @Test
    public void testGetOrder() throws UnexpectedException, FogbowManagerException {
        // set up
        String orderId = getComputeOrderCreationId(OrderState.OPEN);
        Map<String, String> attributes = new HashMap<String, String>();
        attributes.put(FederationUser.MANDATORY_NAME_ATTRIBUTE, "fake-name");
        FederationUser federationUser = new FederationUser("fake-id", attributes);

        // exercise
        ComputeOrder computeOrder = (ComputeOrder) this.ordersController.getOrder(
                orderId, federationUser, InstanceType.COMPUTE);

        // verify
        Assert.assertEquals(computeOrder, this.openOrdersList.getNext());
    }

    // test case: Getting order with when federationUser is null must throw InstanceNotFoundException.
    @Test(expected = InstanceNotFoundException.class)
    public void testGetInvalidOrder() throws FogbowManagerException {
        // setup
        Map<String, String> attributes = new HashMap<String, String>();
        attributes.put(FederationUser.MANDATORY_NAME_ATTRIBUTE, "fake-name");
        FederationUser federationUser = new FederationUser("fake-user", attributes);

        // exercise
        this.ordersController.getOrder("invalid-order-id", federationUser, InstanceType.COMPUTE);
    }

    // test case: Getting an order passing a different InstanceType must raise InstanceNotFoundException.
    @Test(expected = InstanceNotFoundException.class)
    public void testGetOrderWithInvalidInstanceType() throws FogbowManagerException, UnexpectedException {
        // set up
        String orderId = getComputeOrderCreationId(OrderState.OPEN);
        Map<String, String> attributes = new HashMap<String, String>();
        attributes.put(FederationUser.MANDATORY_NAME_ATTRIBUTE, "fake-name");
        FederationUser federationUser = new FederationUser("fake-user", attributes);

        // exercise
        this.ordersController.getOrder(orderId, federationUser, InstanceType.NETWORK);
    }

    // test case: Getting order with when invalid federationUser (any fedUser with another ID)
    // must throw InstanceNotFoundException.
    @Test(expected = UnauthorizedRequestException.class)
    public void testGetOrderWithInvalidFedUser() throws FogbowManagerException {
        // set up
        String orderId = getComputeOrderCreationId(OrderState.OPEN);
        Map<String, String> attributes = new HashMap<String, String>();
        attributes.put(FederationUser.MANDATORY_NAME_ATTRIBUTE, "another-name");
        FederationUser federationUser = new FederationUser("another-id", attributes);

        // exercise
        this.ordersController.getOrder(orderId, federationUser, InstanceType.COMPUTE);
    }

    // test case: Checks if given an order getResourceInstance() returns its instance.
    @Test
    public void testGetResourceInstance() throws Exception {
        // set up
        LocalCloudConnector localCloudConnector = Mockito.mock(LocalCloudConnector.class);

        CloudConnectorFactory cloudConnectorFactory = Mockito.mock(CloudConnectorFactory.class);
        Mockito.when(cloudConnectorFactory.getCloudConnector(Mockito.anyString())).thenReturn(localCloudConnector);

        Order order = createLocalOrder();
        order.setOrderState(OrderState.FULFILLED);

        this.fulfilledOrdersList.addItem(order);

        String instanceId = "instanceid";
        Instance orderInstance = Mockito.spy(new ComputeInstance(instanceId));
        orderInstance.setState(InstanceState.READY);
        order.setInstanceId(instanceId);

        Mockito.doReturn(orderInstance).when(localCloudConnector).getInstance(Mockito.any(Order.class));

        PowerMockito.mockStatic(CloudConnectorFactory.class);
        BDDMockito.given(CloudConnectorFactory.getInstance()).willReturn(cloudConnectorFactory);

        //exercise
        Instance instance = this.ordersController.getResourceInstance(order);

        // verify
        Assert.assertEquals(orderInstance, instance);
    }

    // test case: Tests if getUserAllocation() returns the ComputeAllocation properly.
    @Test
    public void testGetUserAllocation() throws UnexpectedException, InvalidParameterException {
        // set up
        Map<String, String> attributes = new HashMap<String, String>();
        attributes.put(FederationUser.MANDATORY_NAME_ATTRIBUTE, "fake-name");
        FederationUser federationUser = new FederationUser("fake-user", attributes);
        ComputeOrder computeOrder = new ComputeOrder();
        computeOrder.setFederationUser(federationUser);
        computeOrder.setRequestingMember(this.localMember);
        computeOrder.setProvidingMember(this.localMember);
        computeOrder.setOrderState(OrderState.FULFILLED);

        computeOrder.setActualAllocation(new ComputeAllocation(1, 2, 3));

        this.activeOrdersMap.put(computeOrder.getId(), computeOrder);
        this.fulfilledOrdersList.addItem(computeOrder);

        // exercise
        ComputeAllocation allocation = (ComputeAllocation) this.ordersController.getUserAllocation(
                this.localMember, federationUser, InstanceType.COMPUTE);

        // verify
        Assert.assertEquals(computeOrder.getActualAllocation().getInstances(), allocation.getInstances());
        Assert.assertEquals(computeOrder.getActualAllocation().getRam(), allocation.getRam());
        Assert.assertEquals(computeOrder.getActualAllocation().getvCPU(), allocation.getvCPU());
    }

    // test case: Tests if getUserAllocation() throws UnexpectedException when there is no any order
    // with the InstanceType specified.
    @Test(expected = UnexpectedException.class)
    public void testGetUserAllocationWithInvalidInstanceType() throws UnexpectedException, InvalidParameterException {
        // set up
        Map<String, String> attributes = new HashMap<String, String>();
        attributes.put(FederationUser.MANDATORY_NAME_ATTRIBUTE, "fake-name");
        FederationUser federationUser = new FederationUser("fake-user", attributes);
        NetworkOrder networkOrder = new NetworkOrder();
        networkOrder.setFederationUser(federationUser);
        networkOrder.setRequestingMember(this.localMember);
        networkOrder.setProvidingMember(this.localMember);
        networkOrder.setOrderState(OrderState.FULFILLED);

        this.fulfilledOrdersList.addItem(networkOrder);
        this.activeOrdersMap.put(networkOrder.getId(), networkOrder);

        // exercise
        this.ordersController.getUserAllocation(this.localMember, federationUser, InstanceType.NETWORK);
    }

    // test case: Checks if deleting a failed order, this one will be moved to the closed orders list.
    @Test
    public void testDeleteOrderStateFailed() throws UnexpectedException, InvalidParameterException,
            InstanceNotFoundException {
        // set up
        String orderId = getComputeOrderCreationId(OrderState.FAILED);
        ComputeOrder computeOrder = (ComputeOrder) this.activeOrdersMap.get(orderId);

        // verify
        Assert.assertNotNull(this.failedOrdersList.getNext());
        Assert.assertNull(this.closedOrdersList.getNext());

        // exercise
        this.ordersController.deleteOrder(computeOrder);

        // verify
        Order order = this.closedOrdersList.getNext();
        this.failedOrdersList.resetPointer();

        Assert.assertNull(this.failedOrdersList.getNext());
        Assert.assertNotNull(order);
        Assert.assertEquals(computeOrder, order);
        Assert.assertEquals(OrderState.CLOSED, order.getOrderState());
    }

    // test case: Checks if deleting a fulfiled order, this one will be moved to the closed orders list.
    @Test
    public void testDeleteOrderStateFulfilled() throws UnexpectedException, InvalidParameterException,
            InstanceNotFoundException {
        // set up
        String orderId = getComputeOrderCreationId(OrderState.FULFILLED);
        ComputeOrder computeOrder = (ComputeOrder) this.activeOrdersMap.get(orderId);

        // verify
        Assert.assertNotNull(this.fulfilledOrdersList.getNext());
        Assert.assertNull(this.closedOrdersList.getNext());

        // exercise
        this.ordersController.deleteOrder(computeOrder);

        // verify
        Order order = this.closedOrdersList.getNext();

        Assert.assertNull(this.fulfilledOrdersList.getNext());
        Assert.assertNotNull(order);
        Assert.assertEquals(computeOrder, order);
        Assert.assertEquals(OrderState.CLOSED, order.getOrderState());
    }

    // test case: Checks if deleting a spawning order, this one will be moved to the closed orders list.
    @Test
    public void testDeleteOrderStateSpawning() throws UnexpectedException, InvalidParameterException,
            InstanceNotFoundException {
        // set up
        String orderId = getComputeOrderCreationId(OrderState.SPAWNING);
        ComputeOrder computeOrder = (ComputeOrder) this.activeOrdersMap.get(orderId);

        // verify
        Assert.assertNotNull(this.spawningOrdersList.getNext());
        Assert.assertNull(this.closedOrdersList.getNext());

        // exercise
        this.ordersController.deleteOrder(computeOrder);

        // verify
        Order order = this.closedOrdersList.getNext();

        Assert.assertNull(this.spawningOrdersList.getNext());
        Assert.assertNotNull(order);
        Assert.assertEquals(computeOrder, order);
        Assert.assertEquals(OrderState.CLOSED, order.getOrderState());
    }

    // test case: Checks if deleting a pending order, this one will be moved to the closed orders list.
    @Test
    public void testDeleteOrderStatePending() throws UnexpectedException, InvalidParameterException,
            InstanceNotFoundException {
        // set up
        String orderId = getComputeOrderCreationId(OrderState.PENDING);
        ComputeOrder computeOrder = (ComputeOrder) this.activeOrdersMap.get(orderId);

        // verify
        Assert.assertNotNull(this.pendingOrdersList.getNext());
        Assert.assertNull(this.closedOrdersList.getNext());

        // exercise
        this.ordersController.deleteOrder(computeOrder);

        // verify
        Order order = this.closedOrdersList.getNext();
        Assert.assertNull(this.pendingOrdersList.getNext());

        Assert.assertNotNull(order);
        Assert.assertEquals(computeOrder, order);
        Assert.assertEquals(OrderState.CLOSED, order.getOrderState());
    }

    // test case: Checks if deleting a open order, this one will be moved to the closed orders list.
    @Test
    public void testDeleteOrderStateOpen() throws UnexpectedException, InvalidParameterException,
            InstanceNotFoundException {
        // set up
        String orderId = getComputeOrderCreationId(OrderState.OPEN);
        ComputeOrder computeOrder = (ComputeOrder) this.activeOrdersMap.get(orderId);

        // verify
        Assert.assertNotNull(this.openOrdersList.getNext());
        Assert.assertNull(this.closedOrdersList.getNext());

        // exercise
        this.ordersController.deleteOrder(computeOrder);

        // verify
        Order order = this.closedOrdersList.getNext();

        Assert.assertNull(this.openOrdersList.getNext());
        Assert.assertNotNull(order);
        Assert.assertEquals(computeOrder, order);
        Assert.assertEquals(OrderState.CLOSED, order.getOrderState());
    }

    // test case: Deleting a null order must return a FogbowManagerException.
    @Test(expected = FogbowManagerException.class)
    public void testDeleteNullOrder() throws UnexpectedException,
            InstanceNotFoundException {
        // exercise
        this.ordersController.deleteOrder(null);
    }

    private String getComputeOrderCreationId(OrderState orderState) throws InvalidParameterException {
        String orderId;
        Map<String, String> attributes = new HashMap<String, String>();
        attributes.put(FederationUser.MANDATORY_NAME_ATTRIBUTE, "fake-name");
        FederationUser federationUser = new FederationUser("fake-id", attributes);

        ComputeOrder computeOrder = Mockito.spy(new ComputeOrder());
        computeOrder.setFederationUser(federationUser);
        computeOrder.setRequestingMember(this.localMember);
        computeOrder.setProvidingMember(this.localMember);
        computeOrder.setOrderState(orderState);

        orderId = computeOrder.getId();

        this.activeOrdersMap.put(orderId, computeOrder);

        switch (orderState) {
            case OPEN:
                this.openOrdersList.addItem(computeOrder);
                break;
            case PENDING:
                this.pendingOrdersList.addItem(computeOrder);
                break;
            case SPAWNING:
                this.spawningOrdersList.addItem(computeOrder);
                break;
            case FULFILLED:
                this.fulfilledOrdersList.addItem(computeOrder);
                break;
            case FAILED:
                this.failedOrdersList.addItem(computeOrder);
                break;
            case CLOSED:
                this.closedOrdersList.addItem(computeOrder);
        }

        return orderId;
    }

    private Order createLocalOrder() {
        FederationUser federationUser = Mockito.mock(FederationUser.class);
        UserData userData = Mockito.mock(UserData.class);
        String imageName = "fake-image-name";
        String requestingMember = "";
        String providingMember = "";
        String publicKey = "fake-public-key";

        Order localOrder =
                new ComputeOrder(
                        federationUser,
                        requestingMember,
                        providingMember,
                        8,
                        1024,
                        30,
                        imageName,
                        userData,
                        publicKey,
                        null);

        return localOrder;
    }
}
