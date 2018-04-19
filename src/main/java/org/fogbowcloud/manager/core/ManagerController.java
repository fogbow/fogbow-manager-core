package org.fogbowcloud.manager.core;

import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.constants.ConfigurationConstants;
import org.fogbowcloud.manager.core.constants.DefaultConfigurationConstants;
import org.fogbowcloud.manager.core.datastore.ManagerDatastore;
import org.fogbowcloud.manager.core.instanceprovider.InstanceProvider;
import org.fogbowcloud.manager.core.models.orders.Order;
import org.fogbowcloud.manager.core.models.orders.OrderState;
import org.fogbowcloud.manager.core.models.orders.instances.OrderInstance;

public class ManagerController {

	private ManagerDatastore managerDatastore;
	private ManagerScheduledExecutorService attendOpenOrdersExecutor;

	private InstanceProvider localInstanceProvider;
	private InstanceProvider remoteInstanceProvider;

	private static final Logger LOGGER = Logger.getLogger(ManagerController.class);
	
	public ManagerController(Properties properties) {
		this.attendOpenOrdersExecutor = new ManagerScheduledExecutorService(Executors.newScheduledThreadPool(1));		
		this.scheduleExecutorsServices(properties);		
	}

	private void scheduleExecutorsServices(Properties properties) {
		if (!this.attendOpenOrdersExecutor.isScheduled()) {
			String schedulerPeriodStr = properties.getProperty(ConfigurationConstants.OPEN_ORDERS_SCHEDULER_PERIOD_KEY,
					DefaultConfigurationConstants.OPEN_ORDERS_SCHEDULER_PERIOD);
			Long schedulerPeriod = Long.valueOf(schedulerPeriodStr);
			this.attendOpenOrdersExecutor.scheduleAtFixedRate(new TimerTask() {
				@Override
				public void run() {
					try {
						attendOpenOrders();
					} catch (Throwable e) {
						LOGGER.error("Error while checking and submitting open orders", e);
					}
				}
			}, 0, schedulerPeriod);
		}
	}

	/**
	 * Method that try to get an Instance for an Open Order.
	 */
	private void attendOpenOrders() {
		LOGGER.info("Trying to get Instances for Open Orders");
		List<Order> openOrders = this.managerDatastore.getOrderByState(OrderState.OPEN);

		/**
		 * TODO: this method can generate a race condition. For example:
		 * a user can delete a Open Order while this method is trying to get an
		 * Instance for this Order.
		 */
		for (Order order : openOrders) {
			try {
				order.handleOpenOrder();
				OrderInstance orderInstance = null;
				if (order.isLocal()) {
					orderInstance = this.localInstanceProvider.requestInstance(order);
				} else if (order.isRemote()) {
					orderInstance = this.remoteInstanceProvider.requestInstance(order);
				}
				if (!orderInstance.getId().trim().isEmpty()) {
					order.setOrderState(OrderState.SPAWNING);
				} else {
					throw new RuntimeException("OrderInstance Id not generated");
				}
			} catch (Exception e) {
				LOGGER.error("Error while trying to get an Instance for Order: " + System.lineSeparator() + order, e);
				order.setOrderState(OrderState.FAILED);
			}
			this.managerDatastore.updateOrder(order);
		}
	}
	
	/**
	 * TODO: this procedure method change all possibles states of an Order to CLOSED, when user try to delete an Order. 
	 */
	private synchronized void setOrderToClosed(Long id) {
		Order order = queueOrder.pollOrderById(id);
			if (order.getOrderInstance() != null) {
				instanceProvider.deleteInstance(order);
				if (order.getOrderState().equals(OrderState.FULFILLED)) {
					order.setEndfulfilledTimeStamp(new Date().getTime());
				}
			}
			order.setOrderState(OrderState.CLOSED);
			queueOrder.offerOrder(order);
	}

}
