package org.fogbowcloud.manager.core.models.orders;

import java.util.UUID;

import org.fogbowcloud.manager.core.models.ResourceType;
import org.fogbowcloud.manager.core.models.tokens.FederationUser;

public class NetworkOrder extends Order {

    private String gateway;
    private String address;
    private NetworkAllocationMode allocation;

    public NetworkOrder() {
        super(UUID.randomUUID().toString());
    }
    
    /** Creating Order with predefined Id. */
    public NetworkOrder(String id, FederationUser federationUser, String requestingMember, String providingMember,
            String gateway, String address, NetworkAllocationMode allocation) {
        super(id, federationUser, requestingMember, providingMember);
        this.gateway = gateway;
        this.address = address;
        this.allocation = allocation;
    }

    public NetworkOrder(FederationUser federationUser, String requestingMember, String providingMember,
            String gateway, String address, NetworkAllocationMode allocation) {
        this(UUID.randomUUID().toString(), federationUser, requestingMember, providingMember,
                gateway, address, allocation);
    }

    public String getGateway() {
        return gateway;
    }

    public String getAddress() {
        return address;
    }

    public NetworkAllocationMode getAllocation() {
        return allocation;
    }

    @Override
    public ResourceType getType() {
        return ResourceType.NETWORK;
    }
}
