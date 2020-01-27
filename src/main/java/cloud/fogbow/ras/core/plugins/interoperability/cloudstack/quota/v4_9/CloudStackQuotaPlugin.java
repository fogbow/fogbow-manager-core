package cloud.fogbow.ras.core.plugins.interoperability.cloudstack.quota.v4_9;

import cloud.fogbow.common.util.PropertiesUtil;
import cloud.fogbow.common.util.connectivity.cloud.cloudstack.CloudStackHttpClient;
import cloud.fogbow.common.util.connectivity.cloud.cloudstack.CloudStackHttpToFogbowExceptionMapper;
import cloud.fogbow.common.util.connectivity.cloud.cloudstack.CloudStackUrlUtil;
import cloud.fogbow.ras.api.http.response.quotas.allocation.ResourceAllocation;
import cloud.fogbow.ras.core.plugins.interoperability.cloudstack.CloudStackCloudUtils;
import cloud.fogbow.ras.core.plugins.interoperability.cloudstack.compute.v4_9.GetVirtualMachineRequest;
import cloud.fogbow.ras.core.plugins.interoperability.cloudstack.compute.v4_9.GetVirtualMachineResponse;
import cloud.fogbow.ras.core.plugins.interoperability.cloudstack.network.v4_9.GetNetworkRequest;
import cloud.fogbow.ras.core.plugins.interoperability.cloudstack.network.v4_9.GetNetworkResponse;
import cloud.fogbow.ras.core.plugins.interoperability.cloudstack.volume.v4_9.GetVolumeRequest;
import cloud.fogbow.ras.core.plugins.interoperability.cloudstack.volume.v4_9.GetVolumeResponse;
import com.google.common.annotations.VisibleForTesting;
import org.apache.http.client.HttpResponseException;
import org.apache.log4j.Logger;
import cloud.fogbow.ras.core.plugins.interoperability.cloudstack.quota.v4_9.ListResourceLimitsResponse.ResourceLimit;

import cloud.fogbow.common.exceptions.FogbowException;
import cloud.fogbow.common.models.CloudStackUser;
import cloud.fogbow.ras.api.http.response.quotas.ResourceQuota;
import cloud.fogbow.ras.constants.Messages;
import cloud.fogbow.ras.core.plugins.interoperability.QuotaPlugin;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Properties;

public class CloudStackQuotaPlugin implements QuotaPlugin<CloudStackUser> {

    private static final Logger LOGGER = Logger.getLogger(CloudStackQuotaPlugin.class);
    private static final String CLOUDSTACK_URL = "cloudstack_api_url";

    private static final String LIMIT_TYPE_INSTANCES = "0";
    private static final String LIMIT_TYPE_PUBLIC_IP = "1";
    private static final String LIMIT_TYPE_NETWORK = "6";
    private static final String LIMIT_TYPE_CPU = "8";
    private static final String LIMIT_TYPE_MEMORY = "9";
    private static final String LIMIT_TYPE_STORAGE = "10";
    private static final int DOMAIN_LIMIT_NOT_FOUND_VALUE = 0;

    private Properties properties;
    private CloudStackHttpClient client;
    private String cloudStackUrl;

    public CloudStackQuotaPlugin(String confFilePath) {
        this.properties = PropertiesUtil.readProperties(confFilePath);
        this.cloudStackUrl = this.properties.getProperty(CLOUDSTACK_URL);
        this.client = new CloudStackHttpClient();
    }

    @Override
    public ResourceQuota getUserQuota(@NotNull CloudStackUser cloudUser) throws FogbowException {
        ResourceAllocation totalQuota = getTotalQuota(cloudUser);
        ResourceAllocation usedQuota = getUsedQuota(cloudUser);
        return new ResourceQuota(totalQuota, usedQuota);
    }

    @VisibleForTesting
    ResourceAllocation getUsedQuota(@NotNull CloudStackUser cloudUser) throws FogbowException {
        List<GetVirtualMachineResponse.VirtualMachine> virtualMachines = getVirtualMachines(cloudUser);
        List<GetVolumeResponse.Volume> volumes = getVolumes(cloudUser);
        List<GetNetworkResponse.Network> networks = getNetworks(cloudUser);
        List<ListPublicIpAddressResponse.PublicIpAddress> publicIps = getPublicIps(cloudUser);
        return getUsedAllocation(virtualMachines, volumes, networks, publicIps);
    }

    @VisibleForTesting
    List<ListPublicIpAddressResponse.PublicIpAddress> getPublicIps(@NotNull CloudStackUser cloudUser) throws FogbowException {
        ListPublicIpAddressRequest request = new ListPublicIpAddressRequest.Builder().build(this.cloudStackUrl);
        CloudStackUrlUtil.sign(request.getUriBuilder(), cloudUser.getToken());

        String uri = request.getUriBuilder().toString();
        ListPublicIpAddressResponse response = null;
        String jsonResponse = doGetRequest(cloudUser, uri);

        try {
            response = ListPublicIpAddressResponse.fromJson(jsonResponse);
        } catch (HttpResponseException e) {
            throw CloudStackHttpToFogbowExceptionMapper.get(e);
        }

        List<ListPublicIpAddressResponse.PublicIpAddress> publicIps = response.getPublicIpAddresses();
        return publicIps;
    }

    @VisibleForTesting
    String doGetRequest(@NotNull CloudStackUser cloudUser, @NotBlank String uri) throws FogbowException {
        String response = null;

        try {
            LOGGER.debug(Messages.Info.GETTING_QUOTA);
            response = this.client.doGetRequest(uri, cloudUser);
        } catch (HttpResponseException e) {
            LOGGER.debug(Messages.Exception.FAILED_TO_GET_QUOTA);
            throw CloudStackHttpToFogbowExceptionMapper.get(e);
        }

        return response;
    }

    @VisibleForTesting
    List<GetNetworkResponse.Network> getNetworks(@NotNull CloudStackUser cloudUser) throws FogbowException {
        GetNetworkRequest request = new GetNetworkRequest.Builder().build(this.cloudStackUrl);
        CloudStackUrlUtil.sign(request.getUriBuilder(), cloudUser.getToken());

        String uri = request.getUriBuilder().toString();
        String networkJsonResponse = doGetRequest(cloudUser, uri);
        GetNetworkResponse response = null;

        try {
            response = GetNetworkResponse.fromJson(networkJsonResponse);
        } catch (HttpResponseException e) {
            throw CloudStackHttpToFogbowExceptionMapper.get(e);
        }

        return response.getNetworks();
    }

    @VisibleForTesting
    ResourceAllocation getTotalQuota(@NotNull CloudStackUser cloudUser) throws FogbowException {
        List<ListResourceLimitsResponse.ResourceLimit> resourceLimits = getResourceLimits(cloudUser);
        return getTotalAllocation(resourceLimits, cloudUser);
    }

    @VisibleForTesting
    List<GetVolumeResponse.Volume> getVolumes(@NotNull CloudStackUser cloudUser) throws FogbowException {
        GetVolumeRequest volumeRequest = new GetVolumeRequest.Builder().build(this.cloudStackUrl);
        CloudStackUrlUtil.sign(volumeRequest.getUriBuilder(), cloudUser.getToken());

        String uri = volumeRequest.getUriBuilder().toString();
        String volumeJsonResponse = doGetRequest(cloudUser, uri);
        GetVolumeResponse volumeResponse = null;

        try {
            volumeResponse = GetVolumeResponse.fromJson(volumeJsonResponse);
        } catch (HttpResponseException e) {
            throw CloudStackHttpToFogbowExceptionMapper.get(e);
        }

        return volumeResponse.getVolumes();
    }

    @VisibleForTesting
    List<GetVirtualMachineResponse.VirtualMachine> getVirtualMachines(@NotNull CloudStackUser cloudUser) throws FogbowException {
        GetVirtualMachineRequest request = new GetVirtualMachineRequest.Builder().build(this.cloudStackUrl);
        CloudStackUrlUtil.sign(request.getUriBuilder(), cloudUser.getToken());

        String uri = request.getUriBuilder().toString();
        String responseJson = doGetRequest(cloudUser, uri);
        GetVirtualMachineResponse response = null;

        try {
            response = GetVirtualMachineResponse.fromJson(responseJson);
        } catch (HttpResponseException e) {
            throw CloudStackHttpToFogbowExceptionMapper.get(e);
        }

        return response.getVirtualMachines();
    }

    @VisibleForTesting
    List<ListResourceLimitsResponse.ResourceLimit> getResourceLimits(@NotNull CloudStackUser cloudUser) throws FogbowException {
        ListResourceLimitsRequest request = new ListResourceLimitsRequest.Builder()
                .build(this.cloudStackUrl);
        CloudStackUrlUtil.sign(request.getUriBuilder(), cloudUser.getToken());

        String uri = request.getUriBuilder().toString();
        String limitsResponse = doGetRequest(cloudUser, uri);
        ListResourceLimitsResponse response = null;

        try {
            response = ListResourceLimitsResponse.fromJson(limitsResponse);
        } catch (HttpResponseException e) {
            throw CloudStackHttpToFogbowExceptionMapper.get(e);
        }

        return response.getResourceLimits();
    }

    @VisibleForTesting
    ResourceAllocation getUsedAllocation(List<GetVirtualMachineResponse.VirtualMachine> vms,
                                         List<GetVolumeResponse.Volume> volumes,
                                         List<GetNetworkResponse.Network> networks,
                                         List<ListPublicIpAddressResponse.PublicIpAddress> publicIps) {
        int usedPublicIps = getPublicIpAllocation(publicIps);
        int usedNetworks = getNetworkAllocation(networks);
        int usedDisk = getVolumeAllocation(volumes);

        ResourceAllocation usedAllocation = buildComputeAllocation(vms)
                .publicIps(usedPublicIps)
                .networks(usedNetworks)
                .disk(usedDisk)
                .build();

        return usedAllocation;
    }

    @VisibleForTesting
    int getNetworkAllocation(List<GetNetworkResponse.Network> networks) {
        return networks.size();
    }

    @VisibleForTesting
    int getPublicIpAllocation(List<ListPublicIpAddressResponse.PublicIpAddress> publicIps) {
        return publicIps.size();
    }

    @VisibleForTesting
    ResourceAllocation.Builder buildComputeAllocation(List<GetVirtualMachineResponse.VirtualMachine> vms) {
        int usedInstances = vms.size();
        int usedCores = 0;
        int usedRam = 0;

        for (GetVirtualMachineResponse.VirtualMachine vm : vms) {
            usedCores += vm.getCpuNumber();
            usedRam += vm.getMemory();
        }

        ResourceAllocation.Builder builder = ResourceAllocation.builder()
                .vCPU(usedCores)
                .instances(usedInstances)
                .ram(usedRam);

        return builder;
    }

    @VisibleForTesting
    int getVolumeAllocation(List<GetVolumeResponse.Volume> volumes) {
        long sizeInBytes = 0;

        for (GetVolumeResponse.Volume volume: volumes) {
            sizeInBytes += volume.getSize();
        }

        return CloudStackCloudUtils.convertToGigabyte(sizeInBytes);
    }

    @VisibleForTesting
    ResourceLimit getDomainResourceLimit(@NotNull ResourceLimit limit, @NotNull CloudStackUser cloudUser) {
        try {
            limit = doGetDomainResourceLimit(limit.getResourceType(), limit.getDomainId(), cloudUser);
        } catch (Exception ex) {
            limit = new ResourceLimit(limit.getResourceType(), limit.getDomainId(), DOMAIN_LIMIT_NOT_FOUND_VALUE);
        }

        return limit;
    }

    @VisibleForTesting
    ResourceAllocation getTotalAllocation(List<ResourceLimit> resourceLimits, @NotNull CloudStackUser cloudUser) {
        ResourceAllocation.Builder builder = ResourceAllocation.builder();
        int max = 0;

        for (ResourceLimit resourceLimit : resourceLimits) {
            if (resourceLimit.getMax() == -1) {
                resourceLimit = getDomainResourceLimit(resourceLimit, cloudUser);
            }

            max = Integer.valueOf(resourceLimit.getMax());

            switch (resourceLimit.getResourceType()) {
                case LIMIT_TYPE_INSTANCES: builder.instances(max); break;
                case LIMIT_TYPE_PUBLIC_IP: builder.publicIps(max); break;
                case LIMIT_TYPE_STORAGE: builder.disk(max); break;
                case LIMIT_TYPE_NETWORK: builder.networks(max); break;
                case LIMIT_TYPE_CPU: builder.vCPU(max); break;
                case LIMIT_TYPE_MEMORY: builder.ram(max); break;
            }
        }

        ResourceAllocation totalAllocation = builder.build();
        return totalAllocation;
    }

    @VisibleForTesting
    ListResourceLimitsResponse.ResourceLimit doGetDomainResourceLimit(@NotBlank String resourceType, @NotBlank String domainId, @NotNull CloudStackUser cloudUser)
            throws FogbowException {
        ListResourceLimitsRequest request = new ListResourceLimitsRequest.Builder()
                .domainId(domainId)
                .resourceType(resourceType)
                .build(this.cloudStackUrl);

        CloudStackUrlUtil.sign(request.getUriBuilder(), cloudUser.getToken());

        String uri = request.getUriBuilder().toString();
        String limitsResponse = doGetRequest(cloudUser, uri);
        ListResourceLimitsResponse response = null;

        try {
            response = ListResourceLimitsResponse.fromJson(limitsResponse);
        } catch (HttpResponseException e) {
            e.printStackTrace();
        }

        // NOTE(pauloewerton): we're limiting result count by resource type, so request should only return one value
        ListResourceLimitsResponse.ResourceLimit resourceLimit = response.getResourceLimits().listIterator().next();
        return resourceLimit;
    }

    @VisibleForTesting
    void setClient(CloudStackHttpClient client) {
        this.client = client;
    }
}
