package cloud.fogbow.ras.core.plugins.interoperability.cloudstack.sdk.v4_9.volume.model;

import cloud.fogbow.common.exceptions.FogbowException;
import cloud.fogbow.ras.core.plugins.interoperability.cloudstack.CloudstackTestUtils;
import cloud.fogbow.ras.core.plugins.interoperability.cloudstack.sdk.v4_9.volume.model.DeleteVolumeResponse;
import org.apache.http.HttpStatus;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.IOException;

public class DeleteVolumeResponseTest {

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    // test case: When calling the fromJson method, it must verify
    // if It returns the right DeleteVolumeResponse.
    @Test
    public void testFromJsonSuccessfully() throws IOException, FogbowException {
        // set up
        boolean status = true;
        String deleteVolumeResponseJson = CloudstackTestUtils.createDeleteVolumeResponseJson(status, null);

        // execute
        DeleteVolumeResponse deleteVolumeResponse = DeleteVolumeResponse.fromJson(deleteVolumeResponseJson);

        // verify
        Assert.assertEquals(status, deleteVolumeResponse.isSuccess());
    }

    // test case: When calling the fromJson method with error json response,
    // it must verify if It throws a FogbowException.
    @Test
    public void testFromJsonFail() throws IOException, FogbowException {
        // set up
        String errorText = "anyString";
        int errorCode = HttpStatus.SC_BAD_REQUEST;
        String volumesErrorResponseJson = CloudstackTestUtils
                .createDeleteVolumeErrorResponseJson(errorCode, errorText);

        // verify
        this.expectedException.expect(FogbowException.class);
        this.expectedException.expectMessage(errorText);

        // execute
        DeleteVolumeResponse.fromJson(volumesErrorResponseJson);
    }

}
