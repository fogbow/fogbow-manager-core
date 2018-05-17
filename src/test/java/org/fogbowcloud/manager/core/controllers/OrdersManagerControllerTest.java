package org.fogbowcloud.manager.core.controllers;

import org.fogbowcloud.manager.core.BaseUnitTests;
import org.fogbowcloud.manager.core.datastructures.SharedOrderHolders;
import org.fogbowcloud.manager.core.exceptions.OrderManagementException;
import org.fogbowcloud.manager.core.models.linkedList.ChainedList;
import org.fogbowcloud.manager.core.models.orders.ComputeOrder;
import org.fogbowcloud.manager.core.models.orders.Order;
import org.fogbowcloud.manager.core.models.orders.OrderState;
import org.fogbowcloud.manager.core.models.token.Token;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class OrdersManagerControllerTest extends BaseUnitTests {

    private OrdersManagerController ordersManagerController;
    private Map<String, Order> activeOrdersMap;
	private ChainedList openOrdersList;
	private ChainedList pendingOrdersList;
	private ChainedList spawningOrdersList;
	private ChainedList fulfilledOrdersList;
	private ChainedList failedOrdersList;
	private ChainedList closedOrdersList;

    @Before
    public void setUp() {
        this.ordersManagerController = new OrdersManagerController();
        SharedOrderHolders sharedOrderHolders = SharedOrderHolders.getInstance();
        this.activeOrdersMap = sharedOrderHolders.getActiveOrdersMap();
		this.openOrdersList = sharedOrderHolders.getOpenOrdersList();
		this.pendingOrdersList = sharedOrderHolders.getPendingOrdersList();
		this.spawningOrdersList = sharedOrderHolders.getSpawningOrdersList();
		this.fulfilledOrdersList = sharedOrderHolders.getFulfilledOrdersList();
		this.failedOrdersList = sharedOrderHolders.getFailedOrdersList();
		this.closedOrdersList = sharedOrderHolders.getClosedOrdersList();
    }

    @Test
    public void testNewOrderRequest() {
        try {
            OrdersManagerController ordersManagerController = new OrdersManagerController();
            ComputeOrder computeOrder = new ComputeOrder();
            Token localToken = getFakeToken();
            Token federationToken = getFakeToken();
            ordersManagerController.newOrderRequest(computeOrder, localToken, federationToken);
        } catch (OrderManagementException e) {
            Assert.fail();
        }
    }

    @Test
    public void testFailedNewOrderRequestOrderIsNull() {
        try {
            ComputeOrder computeOrder = null;
            Token localToken = getFakeToken();
            Token federationToken = getFakeToken();
            this.ordersManagerController.newOrderRequest(computeOrder, localToken, federationToken);
        } catch (OrderManagementException e) {
            String expectedErrorMessage = "Can't process new order request. Order reference is null.";
            Assert.assertEquals(e.getMessage(), expectedErrorMessage);
        } catch (Exception e) {
            Assert.fail();
        }
    }

    private Token getFakeToken() {
        String fakeAccessId = "0000";
        String fakeUserId = "userId";
        String fakeUserName = "userName";
        Token.User fakeUser = new Token.User(fakeUserId, fakeUserName);
        Date fakeExpirationTime = new Date();
        Map<String, String> fakeAttributes = new HashMap<>();
        return  new Token(fakeAccessId, fakeUser, fakeExpirationTime, fakeAttributes);
    }
    
    @Test
	public void testDeleteOrderStateClosed() throws OrderManagementException {
		String orderId = getComputeOrderCreationId(OrderState.CLOSED);
		ComputeOrder computeOrder = (ComputeOrder) activeOrdersMap.get(orderId);

		this.ordersManagerController.deleteOrder(computeOrder);

		Order test = this.closedOrdersList.getNext();
		Assert.assertNotNull(test);
		Assert.assertEquals(computeOrder, test);
		Assert.assertEquals(OrderState.CLOSED, test.getOrderState());
	}

	@Test
	public void testDeleteOrderStateFailed() throws OrderManagementException {
		String orderId = getComputeOrderCreationId(OrderState.FAILED);
		ComputeOrder computeOrder = (ComputeOrder) activeOrdersMap.get(orderId);

		Assert.assertNull(this.closedOrdersList.getNext());

		this.ordersManagerController.deleteOrder(computeOrder);

		Order test = this.closedOrdersList.getNext();
		Assert.assertNotNull(test);
		Assert.assertEquals(computeOrder, test);
		Assert.assertEquals(OrderState.CLOSED, test.getOrderState());
	}

	@Test
	public void testDeleteOrderStateFulfilled() throws OrderManagementException {
		String orderId = getComputeOrderCreationId(OrderState.FULFILLED);
		ComputeOrder computeOrder = (ComputeOrder) activeOrdersMap.get(orderId);

		Assert.assertNull(this.closedOrdersList.getNext());

		this.ordersManagerController.deleteOrder(computeOrder);

		Order test = this.closedOrdersList.getNext();
		Assert.assertNotNull(test);
		Assert.assertEquals(computeOrder, test);
		Assert.assertEquals(OrderState.CLOSED, test.getOrderState());
	}

	@Test
	public void testDeleteOrderStateSpawning() throws OrderManagementException {
		String orderId = getComputeOrderCreationId(OrderState.SPAWNING);
		ComputeOrder computeOrder = (ComputeOrder) activeOrdersMap.get(orderId);

		Assert.assertNull(this.closedOrdersList.getNext());

		this.ordersManagerController.deleteOrder(computeOrder);

		Order test = this.closedOrdersList.getNext();
		Assert.assertNotNull(test);
		Assert.assertEquals(computeOrder, test);
		Assert.assertEquals(OrderState.CLOSED, test.getOrderState());
	}

	@Test
	public void testDeleteOrderStatePending() throws OrderManagementException {
		String orderId = getComputeOrderCreationId(OrderState.PENDING);
		ComputeOrder computeOrder = (ComputeOrder) activeOrdersMap.get(orderId);

		Assert.assertNull(this.closedOrdersList.getNext());

		this.ordersManagerController.deleteOrder(computeOrder);

		Order test = this.closedOrdersList.getNext();
		Assert.assertNotNull(test);
		Assert.assertEquals(computeOrder, test);
		Assert.assertEquals(OrderState.CLOSED, test.getOrderState());
	}

	@Test
	public void testDeleteOrderStateOpen() throws OrderManagementException {
		String orderId = getComputeOrderCreationId(OrderState.OPEN);
		ComputeOrder computeOrder = (ComputeOrder) activeOrdersMap.get(orderId);

		Assert.assertNull(this.closedOrdersList.getNext());

		this.ordersManagerController.deleteOrder(computeOrder);

		Order test = this.closedOrdersList.getNext();
		Assert.assertNotNull(test);
		Assert.assertEquals(computeOrder, test);
		Assert.assertEquals(OrderState.CLOSED, test.getOrderState());
	}
	
	@Test(expected = OrderManagementException.class)
	public void testDeleteNullOrder() throws OrderManagementException {
		this.ordersManagerController.deleteOrder(null);
	}

	private Token createToken() {
		String accessId = "fake-access-id";
		Token.User tokenUser = this.createTokenUser();
		Date expirationTime = new Date();
		Map<String, String> attributesMap = new HashMap<>();
		return new Token(accessId, tokenUser, expirationTime, attributesMap);
	}

	private Token.User createTokenUser() {
		String tokenUserId = "fake-user-id";
		String tokenUserName = "fake-user-name";
		Token.User tokenUser = new Token.User(tokenUserId, tokenUserName);
		return tokenUser;
	}

	private String getComputeOrderCreationId(OrderState orderState) {
		String orderId = null;

		Token token = this.createToken();

		ComputeOrder computeOrder = Mockito.spy(new ComputeOrder());
		computeOrder.setLocalToken(token);
		computeOrder.setFederationToken(token);
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

}