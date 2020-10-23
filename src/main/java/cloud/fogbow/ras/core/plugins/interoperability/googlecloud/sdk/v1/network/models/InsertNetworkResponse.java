package cloud.fogbow.ras.core.plugins.interoperability.googlecloud.sdk.v1.network.models;

import cloud.fogbow.common.util.GsonHolder;
import cloud.fogbow.ras.core.plugins.interoperability.googlecloud.util.GoogleCloudConstants;
import com.google.gson.annotations.SerializedName;


/**
 * Documentation: https://cloud.google.com/compute/docs/reference/rest/v1/networks
 * <p>
 * Response Example:
 * {
 *  "targetLink": https://www.googleapis.com/compute/v1/projects/chatflutter-5c6ad/global/networks/net1
 * }
 * <p>
 * We use the @SerializedName annotation to specify that the request parameter is not equal to the class field.
 */
public class InsertNetworkResponse {
    @SerializedName(GoogleCloudConstants.Network.TARGET_LINK_KEY_JSON)
    private String targetLink;

    public InsertNetworkResponse(String targetLink){
        this.targetLink = targetLink;
    }

    public String getTargetLink(){
        return this.targetLink;
    }

    public String toJson() {
        return GsonHolder.getInstance().toJson(this);
    }

    public static InsertNetworkResponse fromJson(String json){
        return GsonHolder.getInstance().fromJson(json, InsertNetworkResponse.class);
    }

}