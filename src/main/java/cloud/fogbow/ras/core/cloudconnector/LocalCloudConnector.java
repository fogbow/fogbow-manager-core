package cloud.fogbow.ras.core.cloudconnector;

import cloud.fogbow.common.exceptions.FogbowException;
import cloud.fogbow.common.exceptions.InstanceNotFoundException;
import cloud.fogbow.common.exceptions.InvalidParameterException;
import cloud.fogbow.common.exceptions.UnexpectedException;
import cloud.fogbow.common.models.CloudUser;
import cloud.fogbow.common.models.SystemUser;
import cloud.fogbow.common.util.connectivity.FogbowGenericResponse;
import cloud.fogbow.ras.api.http.response.*;
import cloud.fogbow.ras.api.http.response.quotas.ComputeQuota;
import cloud.fogbow.ras.api.http.response.quotas.Quota;
import cloud.fogbow.ras.api.http.response.securityrules.SecurityRule;
import cloud.fogbow.ras.constants.Messages;
import cloud.fogbow.ras.core.InteroperabilityPluginInstantiator;
import cloud.fogbow.ras.core.SharedOrderHolders;
import cloud.fogbow.ras.core.datastore.DatabaseManager;
import cloud.fogbow.ras.core.models.Operation;
import cloud.fogbow.ras.core.models.ResourceType;
import cloud.fogbow.ras.core.models.UserData;
import cloud.fogbow.ras.core.models.auditing.AuditableRequest;
import cloud.fogbow.ras.core.models.orders.*;
import cloud.fogbow.ras.core.plugins.interoperability.*;
import cloud.fogbow.ras.core.plugins.interoperability.GenericRequestPlugin;
import cloud.fogbow.ras.core.plugins.mapper.SystemToCloudMapperPlugin;
import org.apache.log4j.Logger;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class LocalCloudConnector implements CloudConnector {
    private static final Logger LOGGER = Logger.getLogger(LocalCloudConnector.class);

    private SystemToCloudMapperPlugin mapperPlugin;
    private PublicIpPlugin publicIpPlugin;
    private AttachmentPlugin attachmentPlugin;
    private ComputePlugin computePlugin;
    private ComputeQuotaPlugin computeQuotaPlugin;
    private NetworkPlugin networkPlugin;
    private VolumePlugin volumePlugin;
    private ImagePlugin imagePlugin;
    private SecurityRulePlugin securityRulePlugin;
    private GenericRequestPlugin genericRequestPlugin;

    private boolean auditRequestsOn = true;

    public LocalCloudConnector(String cloudName) {
        InteroperabilityPluginInstantiator instantiator = new InteroperabilityPluginInstantiator();
        this.attachmentPlugin = instantiator.getAttachmentPlugin(cloudName);
        this.computePlugin = instantiator.getComputePlugin(cloudName);
        this.computeQuotaPlugin = instantiator.getComputeQuotaPlugin(cloudName);
        this.networkPlugin = instantiator.getNetworkPlugin(cloudName);
        this.volumePlugin = instantiator.getVolumePlugin(cloudName);
        this.imagePlugin = instantiator.getImagePlugin(cloudName);
        this.publicIpPlugin = instantiator.getPublicIpPlugin(cloudName);
        this.securityRulePlugin = instantiator.getSecurityRulePlugin(cloudName);
        this.genericRequestPlugin = instantiator.getGenericRequestPlugin(cloudName);
        this.mapperPlugin = instantiator.getSystemToCloudMapperPlugin(cloudName);
    }

    @Override
    public String requestInstance(Order order) throws FogbowException {
        CloudUser cloudUser = this.mapperPlugin.map(order.getSystemUser());

        String response = null;
        try {
            response = doRequestInstance(order, cloudUser);
        } catch (Throwable e) {
            response = e.getClass().getName();
            throw e;
        } finally {
            auditRequest(Operation.CREATE, order.getType(), order.getSystemUser(), response);
        }

        return response;
    }

    @Override
    public void deleteInstance(Order order) throws FogbowException {
        CloudUser cloudUser = this.mapperPlugin.map(order.getSystemUser());

        String response = null;
        try {
            doDeleteInstance(order, cloudUser);
        } catch (Throwable e) {
            response = e.getClass().getName();
            throw e;
        } finally {
            auditRequest(Operation.DELETE, order.getType(), order.getSystemUser(), response);
        }
    }

    @Override
    public Instance getInstance(Order order) throws FogbowException {
        CloudUser token = this.mapperPlugin.map(order.getSystemUser());

        String auditableResponse = null;
        Instance instance = null;
        try {
            instance = doGetInstance(order, token);
            instance.setState(InstanceStatus.mapInstanceStateFromOrderState(order.getOrderState()));
            if (instance != null) {
                auditableResponse = instance.toString();
            }
        } catch (Throwable e) {
            auditableResponse = e.getClass().getName();
            throw e;
        } finally {
            auditRequest(Operation.GET, order.getType(), order.getSystemUser(), auditableResponse);
        }

        return instance;
    }

    @Override
    public Quota getUserQuota(SystemUser systemUser, ResourceType resourceType) throws FogbowException {
        CloudUser cloudUser = this.mapperPlugin.map(systemUser);

        String auditableResponse = null;
        Quota quota = null;
        try {
            quota = doGetUserQuota(cloudUser, resourceType);
            if (quota != null) {
                auditableResponse = quota.toString();
            }
        } catch (Throwable e) {
            auditableResponse = e.getClass().getName();
            throw e;
        } finally {
            auditRequest(Operation.GET_USER_QUOTA, resourceType, systemUser, auditableResponse);
        }

        return quota;
    }

    @Override
    public Map<String, String> getAllImages(SystemUser systemUser) throws FogbowException {
        CloudUser cloudUser = this.mapperPlugin.map(systemUser);

        Map<String, String> images = null;
        String auditableResponse = null;
        try {
            images = doGetAllImages(cloudUser);
            if (images != null) {
                auditableResponse = images.toString();
            }
        } catch (Throwable e) {
            auditableResponse = e.getClass().getName();
            throw e;
        } finally {
            auditRequest(Operation.GET_ALL, ResourceType.IMAGE, systemUser, auditableResponse);
        }

        return images;
    }

    @Override
    public Image getImage(String imageId, SystemUser systemUser) throws FogbowException {
        CloudUser cloudUser = this.mapperPlugin.map(systemUser);

        Image image = null;
        String auditableResponse = null;
        try {
            image = doGetImage(imageId, cloudUser);
            if (image != null) {
                auditableResponse = image.toString();
            }
        } catch (Throwable e) {
            auditableResponse = e.getClass().getName();
            throw e;
        } finally {
            auditRequest(Operation.GET, ResourceType.IMAGE, systemUser, auditableResponse);
        }

        return image;
    }

    @Override
    public FogbowGenericResponse genericRequest(String genericRequest, SystemUser systemUser) throws FogbowException {
        CloudUser cloudUser = this.mapperPlugin.map(systemUser);

        FogbowGenericResponse fogbowGenericResponse = null;
        String auditableResponse = null;
        try {
            fogbowGenericResponse = doGenericRequest(genericRequest, cloudUser);
            if (fogbowGenericResponse != null) {
                auditableResponse = fogbowGenericResponse.toString();
            }
        } catch (Throwable e) {
            auditableResponse = e.getClass().getName();
            throw e;
        } finally {
            auditRequest(Operation.CREATE, ResourceType.GENERIC_RESOURCE, systemUser, auditableResponse);
        }

        return fogbowGenericResponse;
    }

    @Override
    public List<SecurityRule> getAllSecurityRules(Order order, SystemUser systemUser) throws FogbowException {
        CloudUser cloudUser = this.mapperPlugin.map(systemUser);

        List<SecurityRule> securityRules = null;
        String auditableResponse = null;
        try {
            securityRules = doGetAllSecurityRules(order, cloudUser);
            if (securityRules != null) {
                auditableResponse = securityRules.toString();
            }
        } catch (Exception e) {
            auditableResponse = e.getClass().getName();
            throw e;
        } finally {
            auditRequest(Operation.GET_ALL, order.getType(), systemUser, auditableResponse);
        }

        return securityRules;
    }

    @Override
    public String requestSecurityRule(Order order, SecurityRule securityRule, SystemUser systemUser) throws FogbowException {
        CloudUser cloudUser = this.mapperPlugin.map(systemUser);

        String response = null;
        try {
            response = doRequestSecurityRule(order, securityRule, cloudUser);
        } catch (Exception e) {
            response = e.getClass().getName();
            throw e;
        } finally {
            auditRequest(Operation.CREATE, order.getType(), systemUser, response);
        }

        return response;
    }

    @Override
    public void deleteSecurityRule(String securityRuleId, SystemUser systemUser) throws FogbowException {
        CloudUser cloudUser = this.mapperPlugin.map(systemUser);

        String response = null;
        try {
            doDeleteSecurityRule(securityRuleId, cloudUser);
        } catch (Exception e) {
            response = e.getClass().getName();
            throw e;
        } finally {
            auditRequest(Operation.DELETE, ResourceType.SECURITY_RULE, systemUser, response);
        }
    }

    private String doRequestInstance(Order order, CloudUser cloudUser) throws FogbowException {
        String requestInstance = null;
        switch (order.getType()) {
            case COMPUTE:
                ComputeOrder computeOrder = (ComputeOrder) order;
                checkComputeOrderConsistency(computeOrder);
                requestInstance = this.computePlugin.requestInstance(computeOrder, cloudUser);
                break;
            case NETWORK:
                NetworkOrder networkOrder = (NetworkOrder) order;
                requestInstance = this.networkPlugin.requestInstance(networkOrder, cloudUser);
                break;
            case VOLUME:
                VolumeOrder volumeOrder = (VolumeOrder) order;
                requestInstance = this.volumePlugin.requestInstance(volumeOrder, cloudUser);
                break;
            case ATTACHMENT:
                AttachmentOrder attachmentOrder = (AttachmentOrder) order;
                checkAttchmentOrderConsistency(attachmentOrder);
                requestInstance = this.attachmentPlugin.requestInstance(attachmentOrder, cloudUser);
                break;
            case PUBLIC_IP:
                PublicIpOrder publicIpOrder = (PublicIpOrder) order;
                checkPublicIpOrderConsistency(publicIpOrder);
                requestInstance = this.publicIpPlugin.requestInstance(publicIpOrder, cloudUser);
                break;
            default:
                throw new UnexpectedException(String.format(Messages.Exception.PLUGIN_FOR_REQUEST_INSTANCE_NOT_IMPLEMENTED, order.getType()));
        }
        if (requestInstance == null) {
            throw new UnexpectedException(Messages.Exception.NULL_VALUE_RETURNED);
        }

        return requestInstance;
    }

    private void doDeleteInstance(Order order, CloudUser cloudUser) throws FogbowException {
        try {
            if (order.getInstanceId() != null) {
                switch (order.getType()) {
                    case COMPUTE:
                        this.computePlugin.deleteInstance(order.getInstanceId(), cloudUser);
                        break;
                    case VOLUME:
                        this.volumePlugin.deleteInstance(order.getInstanceId(), cloudUser);
                        break;
                    case NETWORK:
                        this.networkPlugin.deleteInstance(order.getInstanceId(), cloudUser);
                        break;
                    case ATTACHMENT:
                        this.attachmentPlugin.deleteInstance(order.getInstanceId(), cloudUser);
                        break;
                    case PUBLIC_IP:
                        this.publicIpPlugin.deleteInstance(order.getInstanceId(), cloudUser);
                        break;
                    default:
                        LOGGER.error(String.format(Messages.Error.DELETE_INSTANCE_PLUGIN_NOT_IMPLEMENTED, order.getType()));
                        break;
                }
            } else {
                // If instanceId is null, then there is nothing to do.
                return;
            }
        } catch (InstanceNotFoundException e) {
            // This may happen if the resource-allocation-service crashed after the instance is deleted
            // but before the new state is updated in stable storage.
            LOGGER.warn(Messages.Warn.INSTANCE_ALREADY_DELETED);
            return;
        }
    }

    private Instance doGetInstance(Order order, CloudUser cloudUser) throws FogbowException {
        Instance instance;
        synchronized (order) {
            if (order.getOrderState() == OrderState.CLOSED) {
                throw new InstanceNotFoundException();
            }
            String instanceId = order.getInstanceId();
            if (instanceId != null) {
                instance = getResourceInstance(order, order.getType(), cloudUser);
                // The user believes that the order id is actually the instance id.
                // So we need to set the instance id accordingly before returning the instance.
                instance.setId(order.getId());
            } else {
                // When there is no instance, an empty one is created with the appropriate state
                switch (order.getType()) {
                    case COMPUTE:
                        instance = new ComputeInstance(order.getId());
                        break;
                    case VOLUME:
                        instance = new VolumeInstance(order.getId());
                        break;
                    case NETWORK:
                        instance = new NetworkInstance(order.getId());
                        break;
                    case ATTACHMENT:
                        instance = new AttachmentInstance(order.getId());
                        break;
                    case PUBLIC_IP:
                        instance = new PublicIpInstance(order.getId());
                        break;
                    default:
                        throw new UnexpectedException(Messages.Exception.UNSUPPORTED_REQUEST_TYPE);
                }
            }
            // Setting instance common fields that come from the order object
            instance.setProvider(order.getProvider());
            instance.setCloudName(order.getCloudName());
        }
        return instance;
    }

    private Quota doGetUserQuota(CloudUser token, ResourceType resourceType) throws FogbowException {
        switch (resourceType) {
            case COMPUTE:
                ComputeQuota userQuota = this.computeQuotaPlugin.getUserQuota(token);
                return userQuota;
            default:
                throw new UnexpectedException(String.format(Messages.Exception.QUOTA_ENDPOINT_NOT_IMPLEMENTED, resourceType));
        }
    }

    private Map<String, String> doGetAllImages(CloudUser token) throws FogbowException {
        return this.imagePlugin.getAllImages(token);
    }

    private Image doGetImage(String imageId, CloudUser token) throws FogbowException {
        return this.imagePlugin.getImage(imageId, token);
    }

    private FogbowGenericResponse doGenericRequest(String genericRequest, CloudUser token)
            throws FogbowException {
        return this.genericRequestPlugin.redirectGenericRequest(genericRequest, token);
    }

    private List<SecurityRule> doGetAllSecurityRules(Order order, CloudUser token)
            throws FogbowException {
        return this.securityRulePlugin.getSecurityRules(order, token);
    }

    private String doRequestSecurityRule(Order order, SecurityRule securityRule, CloudUser token)
            throws FogbowException {
        return this.securityRulePlugin.requestSecurityRule(securityRule, order, token);
    }

    private void doDeleteSecurityRule(String securityRuleId, CloudUser token) throws FogbowException {
        this.securityRulePlugin.deleteSecurityRule(securityRuleId, token);
    }

    private Instance getResourceInstance(Order order, ResourceType resourceType, CloudUser cloudUser) throws FogbowException {
        OrderPlugin plugin;
        String instanceId = order.getInstanceId();

        switch (resourceType) {
            case COMPUTE:
                plugin = this.computePlugin;
                break;
            case NETWORK:
                plugin = this.networkPlugin;
                break;
            case VOLUME:
                plugin = this.volumePlugin;
                break;
            case ATTACHMENT:
                plugin = this.attachmentPlugin;
                break;
            case PUBLIC_IP:
                plugin = this.publicIpPlugin;
                break;
            default:
                throw new UnexpectedException(String.format(Messages.Exception.UNSUPPORTED_REQUEST_TYPE, order.getType()));
        }
        Instance instance = plugin.getInstance(instanceId, cloudUser);
        instance = getFullInstance(resourceType, order, instance);
        instance.setProvider(order.getProvider());
        boolean instanceHasFailed = plugin.hasFailed(instance.getCloudState());
        boolean instanceIsReady = plugin.isReady(instance.getCloudState());
        if (instanceHasFailed) instance.setHasFailed();
        if (instanceIsReady) instance.setReady();
        return instance;
    }

    public void switchOffAuditing() {
        this.auditRequestsOn = false;
    }

    private void checkAttchmentOrderConsistency(AttachmentOrder attachmentOrder) throws InvalidParameterException {
        String attachComputeOrderId = attachmentOrder.getComputeOrderId();
        String attachVolumeOrderId = attachmentOrder.getVolumeOrderId();
        Order attachmentComputeOrder = SharedOrderHolders.getInstance().getActiveOrdersMap().get(attachComputeOrderId);
        Order attachmentVolumeOrder = SharedOrderHolders.getInstance().getActiveOrdersMap().get(attachVolumeOrderId);
        SystemUser attachmentOrderSystemUser = attachmentOrder.getSystemUser();
        SystemUser computeOrderSystemUser = attachmentComputeOrder.getSystemUser();
        SystemUser volumeOrderSystemUser = attachmentVolumeOrder.getSystemUser();
        // Check if both the compute and the volume orders belong to the user issuing the attachment order
        if (!attachmentOrderSystemUser.equals(computeOrderSystemUser) ||
                !attachmentOrderSystemUser.equals(volumeOrderSystemUser)) {
            throw new InvalidParameterException(Messages.Exception.TRYING_TO_USE_RESOURCES_FROM_ANOTHER_USER);
        }
        String attachmentProvider = attachmentOrder.getProvider();
        String computeProvider = attachmentComputeOrder.getProvider();
        String volumeProvider = attachmentVolumeOrder.getProvider();
        // Check if both compute and volume belong to the requested provider
        if (!attachmentProvider.equals(computeProvider) || !attachmentProvider.equals(volumeProvider)) {
            throw new InvalidParameterException(Messages.Exception.PROVIDERS_DONT_MATCH);
        }
        // As the order parameter came from the rest API, the Compute and Volume fields are actually
        // ComputeOrder and VolumeOrder Ids, since these are the Ids that are known to users/applications
        // using the API. Thus, before requesting the plugin to create the Attachment, we need to set the
        // ComputeId and the VolumeId.
        attachmentOrder.setComputeId(attachmentComputeOrder.getInstanceId());
        attachmentOrder.setVolumeId(attachmentVolumeOrder.getInstanceId());
    }

    private void checkComputeOrderConsistency(ComputeOrder computeOrder) throws InvalidParameterException {
        // As the order parameter came from the rest API, the NetworkInstanceIds in the order are actually
        // NetworkOrderIds, since these are the Ids that are known to users/applications using the API.
        // Thus, before requesting the plugin to create the Compute, we need to set the NetworkIds.
        List<NetworkOrder> networkOrders = getNetworkOrders(computeOrder.getNetworkOrderIds());
        // Before extracting the networkIds, let us first check whether all networkOrders belong to the
        // user who is requesting the compute instance, and have been created at the same provider where
        // the compute order is to be created.
        SystemUser systemUser = computeOrder.getSystemUser();
        String provider = computeOrder.getProvider();
        for (NetworkOrder order : networkOrders) {
            if (!systemUser.equals(order.getSystemUser()) || !provider.equalsIgnoreCase(order.getProvider())) {
                throw new InvalidParameterException(String.format(Messages.Exception.INVALID_NETWORK_ID_S, order.getId()));
            }
        }
        // Extracting network ids.
        List<String> networkInstanceIds = getNetworkInstanceIdsFromNetworkOrders(networkOrders);
        computeOrder.setNetworkIds(networkInstanceIds);
    }


    private void checkPublicIpOrderConsistency(PublicIpOrder publicIpOrder) throws InvalidParameterException {
        String computeOrderId = publicIpOrder.getComputeOrderId();
        Order computeOrder = SharedOrderHolders.getInstance().getActiveOrdersMap().get(computeOrderId);
        if (computeOrder == null) {
            throw new InvalidParameterException(String.format(Messages.Exception.INVALID_COMPUTE_ID_S, computeOrderId));
        }
        SystemUser publicIpOrderSystemUser = publicIpOrder.getSystemUser();
        SystemUser computeOrderSystemUser = computeOrder.getSystemUser();
        if (!publicIpOrderSystemUser.equals(computeOrderSystemUser)) {
            throw new InvalidParameterException(Messages.Exception.TRYING_TO_USE_RESOURCES_FROM_ANOTHER_USER);
        }
        String computeInstanceId = computeOrder.getInstanceId();
        if (computeInstanceId == null) {
            throw new InvalidParameterException(String.format(Messages.Exception.COMPUTE_INSTANCE_NULL_S, computeOrder.getId()));
        }
    }

    private Instance getFullInstance(ResourceType resourceType, Order order, Instance instance) throws FogbowException {
        switch (resourceType) {
            case COMPUTE:
                return getFullComputeInstance(((ComputeOrder) order), ((ComputeInstance) instance));
            case NETWORK:
                break;
            case VOLUME:
                break;
            case ATTACHMENT:
                return getFullAttachmentInstance(((AttachmentOrder) order), ((AttachmentInstance) instance));
            case PUBLIC_IP:
                return getFullPublicIpInstance(((PublicIpOrder) order), ((PublicIpInstance) instance));
        }
        return instance;
    }

    public ComputeInstance getFullComputeInstance(ComputeOrder order, ComputeInstance instance)
            throws FogbowException {
        ComputeInstance fullInstance = instance;
        String imageId = order.getImageId();
        String imageName = getAllImages(order.getSystemUser()).get(imageId);
        String publicKey = order.getPublicKey();
        List<UserData> userData = order.getUserData();
        Map<String, String> computeNetworks = instance.getNetworks();
        if (computeNetworks == null) {
            computeNetworks = new HashMap<>();
        }
        addNetworkOrderIdsFromComputeOrder(computeNetworks, order);
        fullInstance.setNetworks(computeNetworks);
        fullInstance.setImageId(imageId + " : " + imageName);
        fullInstance.setPublicKey(publicKey);
        fullInstance.setUserData(userData);
        return fullInstance;
    }

    private AttachmentInstance getFullAttachmentInstance(AttachmentOrder order, AttachmentInstance instance) {
        AttachmentInstance fullInstance = instance;
        String savedVolumeId = order.getVolumeId();
        String savedComputeId = order.getComputeId();
        ComputeOrder computeOrder = (ComputeOrder) SharedOrderHolders.getInstance().getActiveOrdersMap().get(savedComputeId);
        VolumeOrder volumeOrder = (VolumeOrder) SharedOrderHolders.getInstance().getActiveOrdersMap().get(savedVolumeId);

        fullInstance.setComputeName(computeOrder.getName());
        fullInstance.setComputeId(computeOrder.getId());
        fullInstance.setVolumeName(volumeOrder.getName());
        fullInstance.setVolumeId(volumeOrder.getId());

        return fullInstance;
    }

    private PublicIpInstance getFullPublicIpInstance(PublicIpOrder order, PublicIpInstance instance) {
        PublicIpInstance publicIpInstance = instance;
        String computeOrderId = order.getComputeId();

        ComputeOrder retrievedComputeOrder = (ComputeOrder) SharedOrderHolders.getInstance().getActiveOrdersMap()
                .get(computeOrderId);

        String computeInstanceName = retrievedComputeOrder.getName();
        String computeInstanceId = retrievedComputeOrder.getId();
        publicIpInstance.setComputeName(computeInstanceName);
        publicIpInstance.setComputeId(computeInstanceId);

        return publicIpInstance;
    }

    private void addNetworkOrderIdsFromComputeOrder(Map<String, String> networks, ComputeOrder order) {
        List<String> networkOrdersId = order.getNetworkOrderIds();

        for (String orderId : networkOrdersId) {
            NetworkOrder networkOrder = (NetworkOrder) SharedOrderHolders.getInstance().getActiveOrdersMap().get(orderId);
            String networkId = networkOrder.getId();
            String networkName = networkOrder.getName();

            networks.put(networkId, networkName);
        }
    }

    private List<NetworkOrder> getNetworkOrders(List<String> networkIds) throws InvalidParameterException {
        List<NetworkOrder> networkOrders = new LinkedList<>();

        for (String orderId : networkIds) {
            Order networkOrder = SharedOrderHolders.getInstance().getActiveOrdersMap().get(orderId);

            if (networkOrder == null) {
                throw new InvalidParameterException(Messages.Exception.INVALID_PARAMETER);
            }
            networkOrders.add((NetworkOrder) networkOrder);
        }
        return networkOrders;
    }

    /**
     * protected visibility for tests
     * @param networkOrders
     */
    protected List<String> getNetworkInstanceIdsFromNetworkOrders(List<NetworkOrder> networkOrders) throws InvalidParameterException {
        List<String> networkInstanceIDs = new LinkedList<String>();

        for (NetworkOrder order : networkOrders) {
            String instanceId = order.getInstanceId();
            networkInstanceIDs.add(instanceId);
        }
        return networkInstanceIDs;
    }

    // Used only in tests

    protected void setMapperPlugin(SystemToCloudMapperPlugin mapperPlugin) {
        this.mapperPlugin = mapperPlugin;
    }
    protected void setPublicIpPlugin(PublicIpPlugin publicIpPlugin) {
        this.publicIpPlugin = publicIpPlugin;
    }

    protected void setAttachmentPlugin(AttachmentPlugin attachmentPlugin) {
        this.attachmentPlugin = attachmentPlugin;
    }

    protected void setComputePlugin(ComputePlugin computePlugin) {
        this.computePlugin = computePlugin;
    }

    protected void setComputeQuotaPlugin(ComputeQuotaPlugin computeQuotaPlugin) {
        this.computeQuotaPlugin = computeQuotaPlugin;
    }

    protected void setNetworkPlugin(NetworkPlugin networkPlugin) {
        this.networkPlugin = networkPlugin;
    }

    protected void setVolumePlugin(VolumePlugin volumePlugin) {
        this.volumePlugin = volumePlugin;
    }

    protected void setImagePlugin(ImagePlugin imagePlugin) {
        this.imagePlugin = imagePlugin;
    }

    protected void setGenericRequestPlugin(GenericRequestPlugin genericRequestPlugin) {
        this.genericRequestPlugin = genericRequestPlugin;
    }

    private void auditRequest(Operation operation, ResourceType resourceType, SystemUser systemUser,
                              String response) throws UnexpectedException {
        if (this.auditRequestsOn) {
            String userId = null, identityProviderId = null;
            if (systemUser != null) {
                userId = systemUser.getId();
                identityProviderId = systemUser.getIdentityProviderId();
            }

            Timestamp currentTimestamp = new Timestamp(System.currentTimeMillis());
            AuditableRequest auditableRequest = new AuditableRequest(currentTimestamp, operation, resourceType, userId, identityProviderId, response);
            DatabaseManager.getInstance().auditRequest(auditableRequest);
        }
    }
}