package cloud.fogbow.ras.core.plugins.interoperability.azure.compute.sdk;

import cloud.fogbow.common.exceptions.*;
import cloud.fogbow.common.models.AzureUser;
import cloud.fogbow.common.util.PropertiesUtil;
import cloud.fogbow.ras.api.http.response.ComputeInstance;
import cloud.fogbow.ras.constants.Messages;
import cloud.fogbow.ras.core.models.orders.ComputeOrder;
import cloud.fogbow.ras.core.plugins.interoperability.ComputePlugin;
import cloud.fogbow.ras.core.plugins.interoperability.azure.compute.AzureGetVirtualMachineRef;
import cloud.fogbow.ras.core.plugins.interoperability.azure.compute.AzureVirtualMachineOperation;
import cloud.fogbow.ras.core.plugins.interoperability.azure.compute.sdk.model.AzureCreateVirtualMachineRef;
import cloud.fogbow.ras.core.plugins.interoperability.azure.compute.sdk.model.AzureGetImageRef;
import cloud.fogbow.ras.core.plugins.interoperability.azure.util.*;
import com.google.common.annotations.VisibleForTesting;
import org.apache.log4j.Logger;

import java.util.List;
import java.util.Properties;

import static software.amazon.awssdk.regions.internal.util.EC2MetadataUtils.getUserData;

public class AzureComputePlugin implements ComputePlugin<AzureUser> {

    private static final Logger LOGGER = Logger.getLogger(AzureComputePlugin.class);

    private AzureVirtualMachineOperation azureVirtualMachineOperation;
    private final String defaultNetworkInterfaceName;

    public AzureComputePlugin(String confFilePath) {
        Properties properties = PropertiesUtil.readProperties(confFilePath);
        this.defaultNetworkInterfaceName = properties.getProperty(AzureConstants.DEFAULT_NETWORK_INTERFACE_NAME_KEY);
        this.azureVirtualMachineOperation = new AzureVirtualMachineOperationSDK();
    }

    @Override
    public boolean isReady(String instanceState) {
        return false;
    }

    @Override
    public boolean hasFailed(String instanceState) {
        return false;
    }

    @Override
    public String requestInstance(ComputeOrder computeOrder, AzureUser azureUser) throws FogbowException {
        LOGGER.info(Messages.Info.REQUESTING_INSTANCE_FROM_PROVIDER);

        String networkInterfaceId = getNetworkInterfaceId(computeOrder, azureUser);
        String virtualMachineSizeName = getVirtualMachineSizeName(computeOrder, azureUser);
        int diskSize = AzureGeneralPolicy.getDisk(computeOrder);
        AzureGetImageRef azureVirtualMachineImage = AzureImageOperationUtil
                .buildAzureVirtualMachineImageBy(computeOrder.getImageId());
        String virtualMachineName = AzureInstancePolicy
                .generateAzureResourceNameBy(computeOrder, azureUser);
        String userData = getUserData();
        String osUserName = computeOrder.getId();
        String osUserPassword = AzureGeneralPolicy.generatePassword();
        String osComputeName = computeOrder.getId();
        String regionName = azureUser.getRegionName();
        String resourceGroupName = azureUser.getResourceGroupName();

        AzureCreateVirtualMachineRef azureCreateVirtualMachineRef = AzureCreateVirtualMachineRef.builder()
                .virtualMachineName(virtualMachineName)
                .azureGetImageRef(azureVirtualMachineImage)
                .networkInterfaceId(networkInterfaceId)
                .diskSize(diskSize)
                .size(virtualMachineSizeName)
                .osComputeName(osComputeName)
                .osUserName(osUserName)
                .osUserPassword(osUserPassword)
                .regionName(regionName)
                .resourceGroupName(resourceGroupName)
                .userData(userData)
                .checkAndBuild();

        return doRequestInstance(computeOrder, azureUser, azureCreateVirtualMachineRef);
    }

    @VisibleForTesting
    String doRequestInstance(ComputeOrder computeOrder, AzureUser azureCloudUser,
                             AzureCreateVirtualMachineRef azureCreateVirtualMachineRef)
            throws UnauthenticatedUserException, UnexpectedException, InstanceNotFoundException, InvalidParameterException {

        this.azureVirtualMachineOperation.doCreateInstance(azureCreateVirtualMachineRef, azureCloudUser);
        return AzureInstancePolicy.generateFogbowInstanceIdBy(computeOrder, azureCloudUser);
    }

    @VisibleForTesting
    String getNetworkInterfaceId(ComputeOrder computeOrder, AzureUser azureCloudUser)
            throws FogbowException {

        List<String> networkIds = computeOrder.getNetworkIds();
        if (networkIds.isEmpty()) {
            return AzureIdBuilder
                    .configure(azureCloudUser)
                    .buildNetworkInterfaceId(this.defaultNetworkInterfaceName);
        } else {
            if (networkIds.size() > AzureGeneralPolicy.MAXIMUM_NETWORK_PER_VIRTUAL_MACHINE) {
                throw new FogbowException(Messages.Error.ERROR_MULTIPLE_NETWORKS_NOT_ALLOWED);
            }

            return networkIds.stream().findFirst().get();
        }
    }

    @VisibleForTesting
    String getVirtualMachineSizeName(ComputeOrder computeOrder, AzureUser azureCloudUser)
            throws FogbowException {

        return this.azureVirtualMachineOperation.findVirtualMachineSizeName(
                computeOrder.getMemory(), computeOrder.getvCPU(),
                azureCloudUser.getRegionName(), azureCloudUser);
    }

    @Override
    public ComputeInstance getInstance(ComputeOrder computeOrder, AzureUser azureUser)
            throws FogbowException {

        LOGGER.info(String.format(Messages.Info.GETTING_INSTANCE_S, computeOrder.getInstanceId()));
        String azureVirtualMachineId = computeOrder.getInstanceId();

        AzureGetVirtualMachineRef azureGetVirtualMachineRef = this.azureVirtualMachineOperation
                .doGetInstance(azureVirtualMachineId, azureUser);

        return buildComputeInstance(azureGetVirtualMachineRef, azureUser);
    }

    @VisibleForTesting
    ComputeInstance buildComputeInstance(AzureGetVirtualMachineRef azureGetVirtualMachineRef, AzureUser azureUser) {
        String virtualMachineName = azureGetVirtualMachineRef.getName();
        String virtualMachineId = AzureIdBuilder
                .configure(azureUser)
                .buildVirtualMachineId(virtualMachineName);
        String cloudState = azureGetVirtualMachineRef.getCloudState();
        int vCPU = azureGetVirtualMachineRef.getvCPU();
        int memory = azureGetVirtualMachineRef.getMemory();
        int disk = azureGetVirtualMachineRef.getDisk();
        List<String> ipAddresses = azureGetVirtualMachineRef.getIpAddresses();

        return new ComputeInstance(virtualMachineId, cloudState, virtualMachineName, vCPU, memory, disk, ipAddresses);
    }

    @Override
    public void deleteInstance(ComputeOrder computeOrder, AzureUser azureUser) throws FogbowException {

    }

    @VisibleForTesting
    void setAzureVirtualMachineOperation(AzureVirtualMachineOperation azureVirtualMachineOperation) {
        this.azureVirtualMachineOperation = azureVirtualMachineOperation;
    }

}
