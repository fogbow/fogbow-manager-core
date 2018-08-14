package org.fogbowcloud.manager.core.processors;

import org.fogbowcloud.manager.core.*;
import org.fogbowcloud.manager.core.cloudconnector.CloudConnectorFactory;
import org.fogbowcloud.manager.core.cloudconnector.LocalCloudConnector;
import org.fogbowcloud.manager.core.cloudconnector.RemoteCloudConnector;
import org.fogbowcloud.manager.core.constants.DefaultConfigurationConstants;
import org.fogbowcloud.manager.core.exceptions.UnexpectedException;
import org.fogbowcloud.manager.core.models.linkedlists.ChainedList;
import org.fogbowcloud.manager.core.models.orders.Order;
import org.fogbowcloud.manager.core.models.orders.OrderState;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.BDDMockito;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@RunWith(PowerMockRunner.class)
@PrepareForTest(CloudConnectorFactory.class)
public class ClosedProcessorTest extends BaseUnitTests {

    private ClosedProcessor closedProcessor;
    
    @SuppressWarnings("unused")
    private RemoteCloudConnector remoteCloudConnector;
    private LocalCloudConnector localCloudConnector;

    @SuppressWarnings("unused")
    private Properties properties;
    private Thread thread;

    @Before
    public void setUp() throws UnexpectedException {
        mockReadOrdersFromDataBase();
        this.properties = new Properties();
        
        HomeDir.getInstance().setPath("src/test/resources/private");
        PropertiesHolder propertiesHolder = PropertiesHolder.getInstance();
        properties = propertiesHolder.getProperties();
        
        this.localCloudConnector = Mockito.mock(LocalCloudConnector.class);
        this.remoteCloudConnector = Mockito.mock(RemoteCloudConnector.class);
        this.closedProcessor = Mockito.spy(new ClosedProcessor(
                DefaultConfigurationConstants.CLOSED_ORDERS_SLEEP_TIME));
    }

    @Override
    public void tearDown() {
        if (this.thread != null) {
            this.thread.interrupt();
            this.thread = null;
        }
        super.tearDown();
    }

    //test case: the closed processor should delete the instances of closed orders and remove them
    //from the closed orders list
	@Test
	public void testProcessClosedLocalOrder() throws Exception {

	    //set up
		String instanceId = "fake-id";
		
		Order localOrder = createLocalOrder(getLocalMemberId());
		localOrder.setInstanceId(instanceId);

        OrderStateTransitioner.activateOrder(localOrder);
		OrderStateTransitioner.transition(localOrder, OrderState.CLOSED);

        CloudConnectorFactory cloudConnectorFactory = Mockito.mock(CloudConnectorFactory.class);
        Mockito.when(cloudConnectorFactory.getCloudConnector(Mockito.anyString())).thenReturn(localCloudConnector);

        PowerMockito.mockStatic(CloudConnectorFactory.class);
        BDDMockito.given(CloudConnectorFactory.getInstance()).willReturn(cloudConnectorFactory);

        //exercise
		this.thread = new Thread(this.closedProcessor);
		this.thread.start();
		Thread.sleep(500);

		//verify
		SharedOrderHolders sharedOrderHolders = SharedOrderHolders.getInstance();
		ChainedList closedOrders = sharedOrderHolders.getClosedOrdersList();
		Map<String, Order> activeOrdersMap = sharedOrderHolders.getActiveOrdersMap();
		assertNull(activeOrdersMap.get(localOrder.getId()));

		closedOrders.resetPointer();
		assertNull(closedOrders.getNext());

        Mockito.verify(this.localCloudConnector, Mockito.times(1)).deleteInstance(Mockito.any(Order.class));

        //TODO: it would be good to verity that the OrderStateTransitioner.deactivateOrder was called. However,
        //it is static thus, hard to mock
	}

	//test case: the order remains closed when the deleteInstance throws an exception
    @Test
    public void testProcessClosedLocalOrderFails() throws Exception {

        //set up
        String instanceId = "fake-id";
        Order localOrder = createLocalOrder(getLocalMemberId());
        localOrder.setInstanceId(instanceId);

        OrderStateTransitioner.activateOrder(localOrder);

        OrderStateTransitioner.transition(localOrder, OrderState.CLOSED);

        Mockito.doThrow(Exception.class)
                .when(this.localCloudConnector)
                .deleteInstance(Mockito.any(Order.class));

        CloudConnectorFactory cloudConnectorFactory = Mockito.mock(CloudConnectorFactory.class);
        Mockito.when(cloudConnectorFactory.getCloudConnector(Mockito.anyString())).thenReturn(localCloudConnector);

        PowerMockito.mockStatic(CloudConnectorFactory.class);
        BDDMockito.given(CloudConnectorFactory.getInstance()).willReturn(cloudConnectorFactory);

        //exercise
        this.thread = new Thread(this.closedProcessor);
        this.thread.start();

        Thread.sleep(500);

        //verify
        SharedOrderHolders sharedOrderHolders = SharedOrderHolders.getInstance();
        ChainedList closedOrders = sharedOrderHolders.getClosedOrdersList();
        Map<String, Order> activeOrdersMap = sharedOrderHolders.getActiveOrdersMap();
        assertEquals(localOrder, activeOrdersMap.get(localOrder.getId()));

        closedOrders.resetPointer();
        assertEquals(localOrder, closedOrders.getNext());

        Mockito.verify(this.localCloudConnector, Mockito.times(1)).deleteInstance(Mockito.any(Order.class));
    }
}
