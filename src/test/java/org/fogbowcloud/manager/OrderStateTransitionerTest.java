package org.fogbowcloud.manager;

import org.fogbowcloud.manager.core.BaseUnitTests;
import org.fogbowcloud.manager.core.HomeDir;
import org.fogbowcloud.manager.core.OrderStateTransitioner;
import org.fogbowcloud.manager.core.SharedOrderHolders;
import org.fogbowcloud.manager.core.datastore.DatabaseManager;
import org.fogbowcloud.manager.core.exceptions.UnexpectedException;
import org.fogbowcloud.manager.core.models.linkedlists.SynchronizedDoublyLinkedList;
import org.fogbowcloud.manager.core.models.orders.Order;
import org.fogbowcloud.manager.core.models.orders.OrderState;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.internal.util.MockUtil;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest({SharedOrderHolders.class, DatabaseManager.class})
public class OrderStateTransitionerTest extends BaseUnitTests {

    private MockUtil mockUtil = new MockUtil();

    private Order createOrder(OrderState orderState) {
        Order order = createLocalOrder(getLocalMemberId());
        order.setOrderState(orderState);
        return order;
    }
    
    @Before
    public void setUp() {
        HomeDir.getInstance().setPath("src/test/resources/private");
    }

    @After
    public void tearDown() {
        SharedOrderHolders instance = SharedOrderHolders.getInstance();

        // Clearing the lists if we are not mocking them
        if (!mockUtil.isMock(instance)) {
            for (OrderState state : OrderState.values()) {
                if (! state.equals(OrderState.DEACTIVATED)) {
                    SynchronizedDoublyLinkedList ordersList = instance.getOrdersList(state);

                    ordersList.resetPointer();
                    Order order;
                    while ((order = ordersList.getNext()) != null) {
                        ordersList.removeItem(order);
                    }
                }
            }
        }
    }

    @Test
    public void testValidTransition() throws UnexpectedException {
        OrderState originState = OrderState.OPEN;
        OrderState destinationState = OrderState.SPAWNING;

        DatabaseManager databaseManager = Mockito.mock(DatabaseManager.class);
        when(databaseManager.readActiveOrders(OrderState.OPEN)).thenReturn(new SynchronizedDoublyLinkedList());
        when(databaseManager.readActiveOrders(OrderState.SPAWNING)).thenReturn(new SynchronizedDoublyLinkedList());
        when(databaseManager.readActiveOrders(OrderState.FAILED)).thenReturn(new SynchronizedDoublyLinkedList());
        when(databaseManager.readActiveOrders(OrderState.FULFILLED)).thenReturn(new SynchronizedDoublyLinkedList());
        when(databaseManager.readActiveOrders(OrderState.PENDING)).thenReturn(new SynchronizedDoublyLinkedList());
        when(databaseManager.readActiveOrders(OrderState.CLOSED)).thenReturn(new SynchronizedDoublyLinkedList());

        PowerMockito.mockStatic(DatabaseManager.class);
        given(DatabaseManager.getInstance()).willReturn(databaseManager);

        SharedOrderHolders orderHolders = SharedOrderHolders.getInstance();

        SynchronizedDoublyLinkedList openOrdersList = orderHolders.getOpenOrdersList();
        SynchronizedDoublyLinkedList spawningOrdersList = orderHolders.getSpawningOrdersList();

        Order order = createOrder(originState);
        openOrdersList.addItem(order);

        OrderStateTransitioner.transition(order, destinationState);
        assertNull(openOrdersList.getNext());
        assertEquals(order, spawningOrdersList.getNext());
    }

    @Test(expected = UnexpectedException.class)
    public void testOriginListCannotBeFound() throws UnexpectedException {
        OrderState originState = OrderState.OPEN;
        OrderState destinationState = OrderState.SPAWNING;

        // origin list will fail to be found
        SharedOrderHolders ordersHolder = Mockito.mock(SharedOrderHolders.class);
        when(ordersHolder.getOrdersList(originState)).thenReturn(null);

        PowerMockito.mockStatic(SharedOrderHolders.class);
        given(SharedOrderHolders.getInstance()).willReturn(ordersHolder);

        Order order = createOrder(originState);
        OrderStateTransitioner.transition(order, destinationState);
    }

    @Test(expected = UnexpectedException.class)
    public void testDestinationListCannotBeFound() throws UnexpectedException {
        OrderState originState = OrderState.OPEN;
        OrderState destinationState = OrderState.SPAWNING;

        SharedOrderHolders ordersHolder = Mockito.mock(SharedOrderHolders.class);
        when(ordersHolder.getOrdersList(originState))
                .thenReturn(new SynchronizedDoublyLinkedList());

        // destination list will fail to be found
        when(ordersHolder.getOrdersList(destinationState)).thenReturn(null);

        PowerMockito.mockStatic(SharedOrderHolders.class);
        given(SharedOrderHolders.getInstance()).willReturn(ordersHolder);

        Order order = createOrder(originState);
        OrderStateTransitioner.transition(order, destinationState);
    }

    @Test(expected = UnexpectedException.class)
    public void testOriginListRemovalFailure() throws UnexpectedException {
        OrderState originState = OrderState.OPEN;
        OrderState destinationState = OrderState.SPAWNING;

        SynchronizedDoublyLinkedList origin = Mockito.mock(SynchronizedDoublyLinkedList.class);
        when(origin.removeItem(any(Order.class))).thenReturn(false);

        SynchronizedDoublyLinkedList destination = Mockito.mock(SynchronizedDoublyLinkedList.class);

        SharedOrderHolders ordersHolder = Mockito.mock(SharedOrderHolders.class);
        when(ordersHolder.getOrdersList(originState)).thenReturn(origin);
        when(ordersHolder.getOrdersList(destinationState)).thenReturn(destination);

        PowerMockito.mockStatic(SharedOrderHolders.class);
        given(SharedOrderHolders.getInstance()).willReturn(ordersHolder);

        Order order = createOrder(originState);
        OrderStateTransitioner.transition(order, destinationState);
    }
}
