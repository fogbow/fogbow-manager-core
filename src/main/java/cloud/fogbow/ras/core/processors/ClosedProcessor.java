package cloud.fogbow.ras.core.processors;

import cloud.fogbow.common.exceptions.FogbowException;
import cloud.fogbow.common.exceptions.InstanceNotFoundException;
import cloud.fogbow.common.exceptions.UnexpectedException;
import cloud.fogbow.common.models.linkedlists.ChainedList;
import cloud.fogbow.ras.constants.Messages;
import cloud.fogbow.ras.core.OrderController;
import cloud.fogbow.ras.core.OrderStateTransitioner;
import cloud.fogbow.ras.core.SharedOrderHolders;
import cloud.fogbow.ras.core.cloudconnector.CloudConnector;
import cloud.fogbow.ras.core.models.Operation;
import cloud.fogbow.ras.core.models.orders.Order;
import org.apache.log4j.Logger;

public class ClosedProcessor implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(ClosedProcessor.class);

    private ChainedList<Order> closedOrders;
    private Long sleepTime;
    private OrderController orderController;

    public ClosedProcessor(OrderController orderController, String sleepTimeStr) {
        SharedOrderHolders sharedOrdersHolder = SharedOrderHolders.getInstance();
        this.closedOrders = sharedOrdersHolder.getClosedOrdersList();
        this.sleepTime = Long.valueOf(sleepTimeStr);
        this.orderController = orderController;
    }

    @Override
    public void run() {
        boolean isActive = true;
        while (isActive) {
            try {
                Order order = this.closedOrders.getNext();
                if (order != null) {
                    processClosedOrder(order);
                } else {
                    this.closedOrders.resetPointer();
                    Thread.sleep(this.sleepTime);
                }
            } catch (InterruptedException e) {
                isActive = false;
                LOGGER.error(Messages.Error.THREAD_HAS_BEEN_INTERRUPTED, e);
            } catch (UnexpectedException e) {
                LOGGER.error(e.getMessage(), e);
            } catch (Throwable e) {
                LOGGER.error(Messages.Error.UNEXPECTED_ERROR, e);
            }
        }
    }

    protected void processClosedOrder(Order order) throws FogbowException {
        synchronized (order) {
            CloudConnector cloudConnector = this.orderController.getCloudConnector(order);
            try {
                cloudConnector.deleteInstance(order);
            } catch (InstanceNotFoundException e) {
                LOGGER.info(String.format(Messages.Info.DELETING_ORDER_INSTANCE_NOT_FOUND, order.getId()), e);
            }

            this.orderController.updateOrderDependencies(order, Operation.DELETE);
            this.orderController.deactivateOrder(order);
        }
    }
}
