package cloud.fogbow.ras.core.plugins.interoperability.cloudstack.publicip.v4_9;

import cloud.fogbow.common.exceptions.FogbowException;
import cloud.fogbow.common.models.CloudStackUser;
import cloud.fogbow.common.util.PropertiesUtil;
import cloud.fogbow.common.util.connectivity.cloud.cloudstack.*;
import cloud.fogbow.ras.constants.Messages;
import cloud.fogbow.ras.api.http.response.InstanceState;
import cloud.fogbow.ras.api.http.response.PublicIpInstance;
import cloud.fogbow.ras.core.models.orders.PublicIpOrder;
import cloud.fogbow.ras.core.plugins.interoperability.PublicIpPlugin;
import org.apache.http.client.HttpResponseException;
import org.apache.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class CloudStackPublicIpPlugin implements PublicIpPlugin<CloudStackUser> {

    private static final Logger LOGGER = Logger.getLogger(CloudStackPublicIpPlugin.class);

    public static final String DEFAULT_NETWORK_ID_KEY = "default_network_id";

    public static final String DEFAULT_SSH_PORT = "22";
    public static final String DEFAULT_PROTOCOL = "TCP";
    public static final String CLOUDSTACK_URL = "cloudstack_api_url";

    private String cloudStackUrl;
    private final String defaultNetworkId;

    private CloudStackHttpClient client;
    private Properties properties;

    // since the ip creation and association involves multiple synchronous and asynchronous requests,
    // we need to keep track of where we are in the process in order to fulfill the operation.
    private static Map<String, CurrentAsyncRequest> publicIpSubState = new HashMap<>();

    public CloudStackPublicIpPlugin(String confFilePath) {
        this.properties = PropertiesUtil.readProperties(confFilePath);
        this.cloudStackUrl = this.properties.getProperty(CLOUDSTACK_URL);

        this.defaultNetworkId = properties.getProperty(DEFAULT_NETWORK_ID_KEY);
        this.client = new CloudStackHttpClient();
    }

    @Override
    public String requestInstance(PublicIpOrder publicIpOrder, String computeInstanceId,
                                  CloudStackUser cloudUser) throws FogbowException {
        String jobId = requestIpAddressAssociation(defaultNetworkId, cloudUser);

        CurrentAsyncRequest currentAsyncRequest = new CurrentAsyncRequest(PublicIpSubState.ASSOCIATING_IP_ADDRESS,
                jobId, computeInstanceId);
        publicIpSubState.put(publicIpOrder.getId(), currentAsyncRequest);

        // we don't have the id of the ip address yet, but since the instance id is only used
        // by the plugin, we can getCloudUser an orderId to an instanceId in the plugin
        return publicIpOrder.getId();
    }

    @Override
    public PublicIpInstance getInstance(String publicIpInstanceId, CloudStackUser cloudUser) throws FogbowException {
        // since we returned the id of the order on requestInstance, publicIpInstanceId
        // should be the id of the order
        String publicIpOrderId = publicIpInstanceId;

        CurrentAsyncRequest currentAsyncRequest = publicIpSubState.get(publicIpOrderId);

        PublicIpInstance result;
        if (currentAsyncRequest == null) {
            result = new PublicIpInstance(null, InstanceState.FAILED, null);
        } else if (currentAsyncRequest.getState().equals(PublicIpSubState.READY)) {
            result = instanceFromCurrentAsyncRequest(currentAsyncRequest, InstanceState.READY);
        } else {
            result = getCurrentInstance(publicIpOrderId, cloudUser);
        }

        return result;
    }

    @Override
    public void deleteInstance(String publicIpInstanceId, String computeInstanceId, CloudStackUser cloudUser)
            throws FogbowException {
        // since we returned the id of the order on requestInstance, publicIpInstanceId
        // should be the id of the order
        String orderId = publicIpInstanceId;
        String ipAddressId = publicIpSubState.get(orderId).getIpInstanceId();

        DisassociateIpAddressRequest disassociateIpAddressRequest = new DisassociateIpAddressRequest.Builder()
                .id(ipAddressId)
                .build(this.cloudStackUrl);

        CloudStackUrlUtil.sign(disassociateIpAddressRequest.getUriBuilder(), cloudUser.getToken());

        try {
            this.client.doGetRequest(disassociateIpAddressRequest.getUriBuilder().toString(),
                    cloudUser);
        } catch (HttpResponseException e) {
            CloudStackHttpToFogbowExceptionMapper.map(e);
        }
    }

    public static String getPublicIpId(String orderId) {
        return publicIpSubState.get(orderId).getIpInstanceId();
    }

    // testing purposes only
    public static void setOrderidToInstanceIdMapping(String orderId, String instanceId) {
        CurrentAsyncRequest currentAsyncRequest = new CurrentAsyncRequest(PublicIpSubState.READY, null, instanceId);
        publicIpSubState.put(orderId, currentAsyncRequest);
    }

    private PublicIpInstance getCurrentInstance(String orderId, CloudStackUser cloudUser) throws FogbowException {
        CurrentAsyncRequest currentAsyncRequest = publicIpSubState.get(orderId);
        String jsonResponse = CloudStackQueryJobResult.getQueryJobResult(this.client, this.cloudStackUrl,
                currentAsyncRequest.getCurrentJobId(), cloudUser);
        CloudStackQueryAsyncJobResponse queryAsyncJobResult = CloudStackQueryAsyncJobResponse.fromJson(jsonResponse);

        PublicIpInstance result;
        switch (queryAsyncJobResult.getJobStatus()) {
            case CloudStackQueryJobResult.PROCESSING:
                result = new PublicIpInstance(null, InstanceState.CREATING, null);
                break;
            case CloudStackQueryJobResult.SUCCESS:
                switch (currentAsyncRequest.getState()) {
                    case ASSOCIATING_IP_ADDRESS:
                        SuccessfulAssociateIpAddressResponse response = SuccessfulAssociateIpAddressResponse.fromJson(jsonResponse);
                        String ipAddressId = response.getIpAddress().getId();
                        currentAsyncRequest.setIpInstanceId(ipAddressId);
                        currentAsyncRequest.setIp(response.getIpAddress().getIpAddress());

                        enableStaticNat(currentAsyncRequest.getComputeInstanceId(), ipAddressId, cloudUser);

                        String createFirewallRuleJobId = createFirewallRule(ipAddressId, cloudUser);
                        currentAsyncRequest.setCurrentJobId(createFirewallRuleJobId);
                        currentAsyncRequest.setState(PublicIpSubState.CREATING_FIREWALL_RULE);
                        result = instanceFromCurrentAsyncRequest(currentAsyncRequest, InstanceState.CREATING);
                        break;
                    case CREATING_FIREWALL_RULE:
                        currentAsyncRequest.setState(PublicIpSubState.READY);
                        result = instanceFromCurrentAsyncRequest(currentAsyncRequest, InstanceState.READY);
                        break;
                    default:
                        result = null;
                        break;
                }
                break;
            case CloudStackQueryJobResult.FAILURE:
                // any failure should lead to a disassociation of the ip address
                deleteInstance(orderId, null, cloudUser);
                result = new PublicIpInstance(null, InstanceState.FAILED, null);
                break;
            default:
                LOGGER.error(Messages.Error.UNEXPECTED_JOB_STATUS);
                result = null;
                break;
        }
        return result;
    }

    private PublicIpInstance instanceFromCurrentAsyncRequest(CurrentAsyncRequest currentAsyncRequest, InstanceState state) {
        String ipInstanceId = currentAsyncRequest == null ? null : currentAsyncRequest.getIpInstanceId();
        String ip = currentAsyncRequest == null ? null : currentAsyncRequest.getIp();
        return new PublicIpInstance(ipInstanceId, state, ip);
    }

    protected String requestIpAddressAssociation(String networkId, CloudStackUser cloudUser)
            throws FogbowException {
        AssociateIpAddressRequest associateIpAddressRequest = new AssociateIpAddressRequest.Builder()
                .networkId(networkId)
                .build(this.cloudStackUrl);

        CloudStackUrlUtil.sign(associateIpAddressRequest.getUriBuilder(), cloudUser.getToken());

        String jsonResponse = null;
        try {
            jsonResponse = this.client
                    .doGetRequest(associateIpAddressRequest.getUriBuilder().toString(),
                            cloudUser);
        } catch (HttpResponseException e) {
            CloudStackHttpToFogbowExceptionMapper.map(e);
        }

        AssociateIpAddressAsyncJobIdResponse associateIpAddressAsyncJobIdResponse = AssociateIpAddressAsyncJobIdResponse
                .fromJson(jsonResponse);

        return associateIpAddressAsyncJobIdResponse.getJobId();
    }

    protected void enableStaticNat(String computeInstanceId, String ipAdressId, CloudStackUser cloudUser)
            throws FogbowException {

        EnableStaticNatRequest enableStaticNatRequest = new EnableStaticNatRequest.Builder()
                .ipAddressId(ipAdressId)
                .virtualMachineId(computeInstanceId)
                .build(this.cloudStackUrl);

        CloudStackUrlUtil
                .sign(enableStaticNatRequest.getUriBuilder(), cloudUser.getToken());

        try {
            this.client.doGetRequest(enableStaticNatRequest.getUriBuilder().toString(), cloudUser);
        } catch (HttpResponseException e) {
            CloudStackHttpToFogbowExceptionMapper.map(e);
        }
    }

    protected String createFirewallRule(String ipAdressId, CloudStackUser cloudUser) throws FogbowException {
        CreateFirewallRuleRequest createFirewallRuleRequest = new CreateFirewallRuleRequest.Builder()
                .protocol(DEFAULT_PROTOCOL)
                .startPort(DEFAULT_SSH_PORT)
                .endPort(DEFAULT_SSH_PORT)
                .ipAddressId(ipAdressId)
                .build(this.cloudStackUrl);

        CloudStackUrlUtil.sign(createFirewallRuleRequest.getUriBuilder(), cloudUser.getToken());

        String jsonResponse = null;
        try {
            jsonResponse = this.client.doGetRequest(createFirewallRuleRequest.getUriBuilder().toString(), cloudUser);
        } catch (HttpResponseException e) {
            CloudStackHttpToFogbowExceptionMapper.map(e);
        }

        CreateFirewallRuleAsyncResponse response = CreateFirewallRuleAsyncResponse.fromJson(jsonResponse);
        return response.getJobId();
    }

    private enum PublicIpSubState {
        ASSOCIATING_IP_ADDRESS, CREATING_FIREWALL_RULE, READY
    }

    private static class CurrentAsyncRequest {

        private PublicIpSubState state;

        private String currentJobId;

        private String ipInstanceId;
        private String ip;

        private String computeInstanceId;

        public CurrentAsyncRequest(PublicIpSubState state, String currentJobId, String computeInstanceId) {
            this.state = state;
            this.currentJobId = currentJobId;
            this.computeInstanceId = computeInstanceId;
        }

        public PublicIpSubState getState() {
            return state;
        }

        public String getCurrentJobId() {
            return currentJobId;
        }

        public String getComputeInstanceId() {
            return computeInstanceId;
        }

        public void setState(PublicIpSubState state) {
            this.state = state;
        }

        public void setCurrentJobId(String currentJobId) {
            this.currentJobId = currentJobId;
        }

        public String getIp() {
            return ip;
        }

        public void setIp(String ip) {
            this.ip = ip;
        }

        public String getIpInstanceId() {
            return ipInstanceId;
        }

        public void setIpInstanceId(String ipInstanceId) {
            this.ipInstanceId = ipInstanceId;
        }
    }

}
