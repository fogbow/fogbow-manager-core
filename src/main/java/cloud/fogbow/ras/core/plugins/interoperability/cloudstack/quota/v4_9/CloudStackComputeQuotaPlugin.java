package cloud.fogbow.ras.core.plugins.interoperability.cloudstack.quota.v4_9;

import cloud.fogbow.common.exceptions.FogbowException;
import cloud.fogbow.common.models.CloudStackUser;
import cloud.fogbow.common.util.PropertiesUtil;
import cloud.fogbow.common.util.connectivity.cloud.cloudstack.CloudStackHttpClient;
import cloud.fogbow.common.util.connectivity.cloud.cloudstack.CloudStackHttpToFogbowExceptionMapper;
import cloud.fogbow.common.util.connectivity.cloud.cloudstack.CloudStackUrlUtil;
import cloud.fogbow.ras.api.http.response.quotas.ComputeQuota;
import cloud.fogbow.ras.api.http.response.quotas.allocation.ComputeAllocation;
import cloud.fogbow.ras.constants.Messages;
import cloud.fogbow.ras.core.plugins.interoperability.ComputeQuotaPlugin;
import cloud.fogbow.ras.core.plugins.interoperability.cloudstack.CloudStackCloudUtils;
import cloud.fogbow.ras.core.plugins.interoperability.cloudstack.compute.v4_9.GetVirtualMachineRequest;
import cloud.fogbow.ras.core.plugins.interoperability.cloudstack.compute.v4_9.GetVirtualMachineResponse;
import com.google.common.annotations.VisibleForTesting;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.utils.URIBuilder;
import org.apache.log4j.Logger;

import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Properties;

public class CloudStackComputeQuotaPlugin implements ComputeQuotaPlugin<CloudStackUser> {
    private static final Logger LOGGER = Logger.getLogger(CloudStackComputeQuotaPlugin.class);

    private CloudStackHttpClient client;
    private String cloudStackUrl;

    public CloudStackComputeQuotaPlugin(String confFilePath) {
        Properties properties = PropertiesUtil.readProperties(confFilePath);
        this.cloudStackUrl = properties.getProperty(CloudStackCloudUtils.CLOUDSTACK_URL_CONFIG);
        this.client = new CloudStackHttpClient();
    }

    @Override
    public ComputeQuota getUserQuota(@NotNull CloudStackUser cloudStackUser) throws FogbowException {
        LOGGER.info(Messages.Info.GETTING_QUOTAFROM_PROVIDER);
        ComputeAllocation totalQuota = buildTotalComputeAllocation(cloudStackUser);
        ComputeAllocation usedQuota = buildUsedComputeAllocation(cloudStackUser);
        return new ComputeQuota(totalQuota, usedQuota);
    }

    @NotNull
    @VisibleForTesting
    ListResourceLimitsResponse requestResourcesLimits(
            @NotNull ListResourceLimitsRequest request,
            @NotNull CloudStackUser cloudStackUser) throws FogbowException {

        URIBuilder uriRequest = request.getUriBuilder();
        CloudStackUrlUtil.sign(uriRequest, cloudStackUser.getToken());

        try {
            String responseStr = CloudStackCloudUtils.doRequest(this.client,
                    uriRequest.toString(), cloudStackUser);
            return ListResourceLimitsResponse.fromJson(responseStr);
        } catch (HttpResponseException e) {
            throw CloudStackHttpToFogbowExceptionMapper.get(e);
        }
    }

    @NotNull
    @VisibleForTesting
    List<GetVirtualMachineResponse.VirtualMachine> getVirtualMachines(
            @NotNull CloudStackUser cloudStackUser) throws FogbowException {

        GetVirtualMachineRequest request = new GetVirtualMachineRequest.Builder()
                .build(this.cloudStackUrl);
        GetVirtualMachineResponse response = CloudStackCloudUtils.requestGetVirtualMachine(
                this.client, request, cloudStackUser);
        return response.getVirtualMachines();
    }

    @NotNull
    @VisibleForTesting
    ComputeAllocation buildUsedComputeAllocation(@NotNull CloudStackUser cloudStackUser)
            throws FogbowException {

        int vCpu = 0;
        int ram = 0;
        List<GetVirtualMachineResponse.VirtualMachine> vms = getVirtualMachines(cloudStackUser);
        int instances = vms.size();
        for (GetVirtualMachineResponse.VirtualMachine vm : vms) {
            vCpu += vm.getCpuNumber();
            ram += vm.getMemory();
        }
        return new ComputeAllocation(vCpu, ram, instances);
    }

    /**
     * Returns the Account Resource Limit whether the max is not unlimited(-1) otherwise
     * returns Domain Resource Limit.
     */
    @NotNull
    @VisibleForTesting
    ListResourceLimitsResponse.ResourceLimit normalizeResourceLimit(
            @NotNull ListResourceLimitsResponse.ResourceLimit accountResourceLimit,
            @NotNull CloudStackUser cloudStackUser) throws FogbowException {

        if (accountResourceLimit.getMax() == CloudStackCloudUtils.UNLIMITED_ACCOUNT_QUOTA) {
            return getDomainResourceLimit(accountResourceLimit.getResourceType(),
                    accountResourceLimit.getDomainId(), cloudStackUser);
        }
        return accountResourceLimit;
    }

    @NotNull
    @VisibleForTesting
    List<ListResourceLimitsResponse.ResourceLimit> getResourcesLimits(
            @NotNull CloudStackUser cloudStackUser) throws FogbowException {

        ListResourceLimitsRequest request = new ListResourceLimitsRequest.Builder()
                .build(this.cloudStackUrl);
        ListResourceLimitsResponse response = requestResourcesLimits(request, cloudStackUser);
        return response.getResourceLimits();
    }

    @NotNull
    @VisibleForTesting
    ComputeAllocation buildTotalComputeAllocation(@NotNull CloudStackUser cloudStackUser)
            throws FogbowException {

        int vCpu = Integer.MAX_VALUE;
        int ram = Integer.MAX_VALUE;
        int instances = Integer.MAX_VALUE;
        List<ListResourceLimitsResponse.ResourceLimit> resourceLimits = getResourcesLimits(cloudStackUser);
        for (ListResourceLimitsResponse.ResourceLimit resourceLimit : resourceLimits) {
            try {
                resourceLimit = normalizeResourceLimit(resourceLimit, cloudStackUser);

                switch (resourceLimit.getResourceType()) {
                    case CloudStackCloudUtils.INSTANCES_LIMIT_TYPE:
                        instances = resourceLimit.getMax();
                        break;
                    case CloudStackCloudUtils.CPU_LIMIT_TYPE:
                        vCpu = resourceLimit.getMax();
                        break;
                    case CloudStackCloudUtils.MEMORY_LIMIT_TYPE:
                        ram = resourceLimit.getMax();
                        break;
                }
            } catch (FogbowException e) {
                LOGGER.warn(Messages.Warn.UNABLE_TO_RETRIEVE_RESOURCE_LIMIT, e);
            }
        }

        return new ComputeAllocation(vCpu, ram, instances);
    }

    /**
     * Returns an unique Resource Limit when It's specified the domainId and the resourceType
     * in the CloudStack request.
     */
    @NotNull
    @VisibleForTesting
    ListResourceLimitsResponse.ResourceLimit getDomainResourceLimit(String resourceType,
                                                                    String domainId,
                                                                    @NotNull CloudStackUser cloudStackUser)
            throws FogbowException {

        ListResourceLimitsRequest request = new ListResourceLimitsRequest.Builder()
                .domainId(domainId)
                .resourceType(resourceType)
                .build(this.cloudStackUrl);

        ListResourceLimitsResponse listResourceLimitsResponse = requestResourcesLimits(
                request, cloudStackUser);
        List<ListResourceLimitsResponse.ResourceLimit> resourceLimits = listResourceLimitsResponse.
                getResourceLimits();
        return resourceLimits.listIterator().next();
    }

    @VisibleForTesting
    void setClient(CloudStackHttpClient client) {
        this.client = client;
    }

}
