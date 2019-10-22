package cloud.fogbow.ras.core.plugins.interoperability.cloudstack;

import cloud.fogbow.common.util.connectivity.cloud.cloudstack.CloudStackRequest;
import cloud.fogbow.ras.core.plugins.interoperability.cloudstack.attachment.v4_9.AttachVolumeRequest;
import cloud.fogbow.ras.core.plugins.interoperability.cloudstack.compute.v4_9.DeployVirtualMachineRequest;
import cloud.fogbow.ras.core.plugins.interoperability.cloudstack.compute.v4_9.DestroyVirtualMachineRequest;
import cloud.fogbow.ras.core.plugins.interoperability.cloudstack.compute.v4_9.GetVirtualMachineRequest;
import cloud.fogbow.ras.core.plugins.interoperability.cloudstack.network.v4_9.CreateNetworkRequest;
import cloud.fogbow.ras.core.plugins.interoperability.cloudstack.network.v4_9.DeleteNetworkRequest;
import cloud.fogbow.ras.core.plugins.interoperability.cloudstack.network.v4_9.GetNetworkRequest;
import cloud.fogbow.ras.core.plugins.interoperability.cloudstack.quota.v4_9.ListResourceLimitsRequest;
import org.mockito.ArgumentMatcher;

public class RequestMatcher<T extends CloudStackRequest> extends ArgumentMatcher<T> {

    private CloudStackRequest internalRequest;

    private RequestMatcher(CloudStackRequest cloudStackRequest) {
        this.internalRequest = cloudStackRequest;
    }

    @Override
    public boolean matches(Object externalObj)  {
        try {
            CloudStackRequest externalRequest = (CloudStackRequest) externalObj;
            String internalURL = this.internalRequest.getUriBuilder().toString();
            String externalURL = externalRequest.getUriBuilder().toString();

            return internalURL.equals(externalURL);
        } catch (Exception e) {
            return false;
        }
    }

    public static class DeployVirtualMachine extends RequestMatcher<DeployVirtualMachineRequest> {
        public DeployVirtualMachine(CloudStackRequest cloudStackRequest) {
            super(cloudStackRequest);
        }
    }

    public static class GetVirtualMachine extends RequestMatcher<GetVirtualMachineRequest> {
        public GetVirtualMachine(CloudStackRequest cloudStackRequest) {
            super(cloudStackRequest);
        }
    }

    public static class DestroyVirtualMachine extends RequestMatcher<DestroyVirtualMachineRequest> {
        public DestroyVirtualMachine(CloudStackRequest cloudStackRequest) {
            super(cloudStackRequest);
        }
    }

    public static class CreateNetwork extends RequestMatcher<CreateNetworkRequest> {
        public CreateNetwork(CloudStackRequest cloudStackRequest) {
            super(cloudStackRequest);
        }
    }

    public static class DeleteNetwork extends RequestMatcher<DeleteNetworkRequest> {
        public DeleteNetwork(CloudStackRequest cloudStackRequest) {
            super(cloudStackRequest);
        }
    }

    public static class GetNetwork extends RequestMatcher<GetNetworkRequest> {
        public GetNetwork(CloudStackRequest cloudStackRequest) {
            super(cloudStackRequest);
        }
    }

    public static class AttachVolume extends RequestMatcher<AttachVolumeRequest> {
        public AttachVolume(CloudStackRequest cloudStackRequest) {
            super(cloudStackRequest);
        }
    }

    public static class ListResourceLimits extends RequestMatcher<ListResourceLimitsRequest> {
        public ListResourceLimits(CloudStackRequest cloudStackRequest) {
            super(cloudStackRequest);
        }
    }
}
