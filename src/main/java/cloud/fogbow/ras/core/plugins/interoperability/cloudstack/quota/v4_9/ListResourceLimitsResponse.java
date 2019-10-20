package cloud.fogbow.ras.core.plugins.interoperability.cloudstack.quota.v4_9;

import cloud.fogbow.common.util.GsonHolder;
import com.google.gson.annotations.SerializedName;

import javax.validation.constraints.NotNull;
import java.util.List;

import static cloud.fogbow.common.constants.CloudStackConstants.Quota.*;

/**
 * Documentation: https://cloudstack.apache.org/api/apidocs-4.9/apis/listResourceLimits.html
 * <p>
 * Response example:
 * {
 * "listresourcelimitsresponse": {
 * "count": 1,
 * "resourcelimit": [{
 * "account": "fogbow-rec",
 * "domainid": "0dbcf598-4d56-4b8a-8a6f-343fd0956392",
 * "domain": "FOGBOW",
 * "resourcetype": "0",
 * "max": 100
 * }]
 * }
 * }
 */
public class ListResourceLimitsResponse {

    @SerializedName(LIST_RESOURCE_LIMITS_KEY_JSON)
    private ResourceLimitsResponse response;

    public List<ResourceLimit> getResourceLimits() {
        return response.resourceLimits;
    }

    @NotNull
    public static ListResourceLimitsResponse fromJson(String json) {
        return GsonHolder.getInstance().fromJson(json, ListResourceLimitsResponse.class);
    }

    public class ResourceLimitsResponse {

        @SerializedName(RESOURCE_LIMIT_KEY_JSON)
        private List<ResourceLimit> resourceLimits;
    }

    public static class ResourceLimit {

        @SerializedName(RESOURCE_TYPE_KEY_JSON)
        private String resourceType;

        @SerializedName(DOMAIN_ID_KEY_JSON)
        private String domainId;

        @SerializedName(MAX_KEY_JSON)
        private int max;

        public String getResourceType() {
            return resourceType;
        }

        public String getDomainId() {
            return domainId;
        }

        public int getMax() {
            return max;
        }
    }
}
