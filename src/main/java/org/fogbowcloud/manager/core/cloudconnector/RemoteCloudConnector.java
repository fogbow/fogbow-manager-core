package org.fogbowcloud.manager.core.cloudconnector;

import org.apache.log4j.Logger;
import org.fogbowcloud.manager.core.exceptions.FogbowManagerException;
import org.fogbowcloud.manager.core.intercomponent.xmpp.requesters.*;
import org.fogbowcloud.manager.core.models.images.Image;
import org.fogbowcloud.manager.core.models.instances.Instance;
import org.fogbowcloud.manager.core.models.instances.InstanceType;
import org.fogbowcloud.manager.core.models.orders.Order;
import org.fogbowcloud.manager.core.models.quotas.Quota;
import org.fogbowcloud.manager.core.models.token.FederationUser;

import java.util.HashMap;

public class RemoteCloudConnector implements CloudConnector {

    private static final Logger LOGGER = Logger.getLogger(RemoteCloudConnector.class);

    private String destinationMember;

    public RemoteCloudConnector(String memberId) {
        this.destinationMember = memberId;
    }

    @Override
    public String requestInstance(Order order) throws FogbowManagerException {
        RemoteCreateOrderRequest remoteCreateOrderRequest = new RemoteCreateOrderRequest(order);
        remoteCreateOrderRequest.send();
        
        return null;
    }

    @Override
    public void deleteInstance(Order order) throws FogbowManagerException {
        RemoteDeleteOrderRequest remoteDeleteOrderRequest = new RemoteDeleteOrderRequest(order);
		remoteDeleteOrderRequest.send();
    }

    @Override
    public Instance getInstance(Order order) throws FogbowManagerException {
        RemoteGetOrderRequest remoteGetOrderRequest = new RemoteGetOrderRequest(order);
        Instance instance = remoteGetOrderRequest.send();
        return instance;
    }

    @Override
    public Quota getUserQuota(FederationUser federationUser, InstanceType instanceType) throws FogbowManagerException {

        RemoteGetUserQuotaRequest remoteGetUserQuotaRequest = new RemoteGetUserQuotaRequest(this.destinationMember,
                federationUser, instanceType);
        Quota quota = remoteGetUserQuotaRequest.send();
        return quota;
    }

    @Override
    public HashMap<String, String> getAllImages(FederationUser federationUser) throws FogbowManagerException {

        RemoteGetAllImagesRequest remoteGetAllImagesRequest = new RemoteGetAllImagesRequest(this.destinationMember,
                federationUser);
        HashMap<String, String> imagesMap = remoteGetAllImagesRequest.send();
        return imagesMap;
    }

    @Override
    public Image getImage(String imageId, FederationUser federationUser) throws FogbowManagerException {

        RemoteGetImageRequest remoteGetImageRequest = new RemoteGetImageRequest(this.destinationMember, imageId,
                federationUser);
        Image image = remoteGetImageRequest.send();
        return image;    }
}
