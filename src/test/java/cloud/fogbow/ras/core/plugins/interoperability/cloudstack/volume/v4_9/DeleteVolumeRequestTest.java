package cloud.fogbow.ras.core.plugins.interoperability.cloudstack.volume.v4_9;

import cloud.fogbow.common.constants.CloudStackConstants;
import cloud.fogbow.common.exceptions.InvalidParameterException;
import cloud.fogbow.common.util.connectivity.cloud.cloudstack.CloudStackUrlUtil;
import cloud.fogbow.ras.core.plugins.interoperability.cloudstack.CloudstackTestUtils;
import org.apache.http.client.utils.URIBuilder;
import org.junit.Assert;
import org.junit.Test;

public class DeleteVolumeRequestTest {

    // test case: When calling the build method, it must verify if It generates the right URL.
    @Test
    public void testBuildSuccessfully() throws InvalidParameterException {
        // set up
        URIBuilder uriBuilder = CloudStackUrlUtil.createURIBuilder(
                CloudstackTestUtils.CLOUDSTACK_URL_DEFAULT,
                CloudStackConstants.Volume.DELETE_VOLUME_COMMAND);
        String urlBaseExpected = uriBuilder.toString();
        String volumeId = "volumeId";

        String volumeIdStructureUrl = String.format(
                "%s=%s", CloudStackConstants.Volume.ID_KEY_JSON, volumeId);

        String[] urlStructure = new String[] {
                urlBaseExpected,
                volumeIdStructureUrl
        };
        String urlExpectedStr = String.join(
                CloudstackTestUtils.AND_OPERATION_URL_PARAMETER, urlStructure);

        // exercise
        DeleteVolumeRequest deleteVolumeRequest = new DeleteVolumeRequest.Builder()
                .id(volumeId)
                .build(CloudstackTestUtils.CLOUDSTACK_URL_DEFAULT);
        String deleteVolumeRequestUrl = deleteVolumeRequest.getUriBuilder().toString();

        // verify
        Assert.assertEquals(urlExpectedStr, deleteVolumeRequestUrl);
    }

    // test case: When calling the build method with a null parameter,
    // it must verify if It throws an InvalidParameterException.
    @Test(expected = InvalidParameterException.class)
    public void testBuildFail() throws InvalidParameterException {
        // exercise and verify
        new DeleteVolumeRequest.Builder().build(null);
    }

}
