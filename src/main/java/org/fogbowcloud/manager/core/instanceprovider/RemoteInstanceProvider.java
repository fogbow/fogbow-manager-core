package org.fogbowcloud.manager.core.instanceprovider;

import org.fogbowcloud.manager.core.models.orders.Order;
import org.fogbowcloud.manager.core.models.orders.instances.OrderInstance;
import org.fogbowcloud.manager.core.models.token.Token;

public class RemoteInstanceProvider implements InstanceProvider {

    @Override
    public String requestInstance(Order order) throws Exception {
        return null;
    }

    @Override
    public OrderInstance getInstance(Order order) {
        return null;
    }

    @Override
    public void deleteInstance(Token localToken, OrderInstance orderInstance) throws Exception {

    }
}
