package org.fogbowcloud.manager.core.plugins.compute.openstack;

import org.fogbowcloud.manager.core.models.Flavor;
import org.fogbowcloud.manager.core.models.exceptions.RequestException;
import org.fogbowcloud.manager.core.models.orders.ComputeOrder;
import org.fogbowcloud.manager.core.models.orders.UserData;
import org.fogbowcloud.manager.core.models.orders.instances.ComputeOrderInstance;
import org.fogbowcloud.manager.core.models.orders.instances.InstanceState;
import org.fogbowcloud.manager.core.models.token.Token;
import org.fogbowcloud.manager.core.plugins.compute.LaunchCommandGenerator;
import org.fogbowcloud.manager.core.plugins.compute.util.CloudInitUserDataBuilder;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.*;

public class TestOpenStackComputePlugin {

    protected static final String FAKE_USER_DATA_FILE = "fake-extra-user-data";
    protected static final String FAKE_EXTRA_USER_DATA_FILE_TYPE = "fake-file-type";
    protected static final String FAKE_FLAVOR_ID = "fake-flavor-id";
    protected static final String FAKE_NET_ID = "fake-net-id";
    protected static final String FAKE_KEYNAME = "fake-keyname";
    protected static final String FAKE_ENDPOINT = "fake-endpoint";
    protected static final String FAKE_IMAGE_ID = "fake-image-id";
    protected static final String FAKE_INSTANCE_ID = "fake-instance-id";
    protected static final String FAKE_POST_RETURN = "{\"server\": {\"id\": \"fake-instance-id\"}}";
    protected static final String FAKE_GET_RETURN_ACTIVE_INSTANCE = "{\"server\": {\"id\": \"fake-instance-id\", \"status\": \"active\"}}";
    protected static final String FAKE_GET_RETURN_FAILED_INSTANCE = "{\"server\": {\"id\": \"fake-instance-id\", \"status\": \"error\"}}";
    protected static final String FAKE_GET_RETURN_INACTIVE_INSTANCE = "{\"server\": {\"id\": \"fake-instance-id\", \"status\": \"build\"}}";
    protected static final String INVALID_FAKE_POST_RETURN = "invalid";
    private static final String COMPUTE_NOVAV2_URL_KEY = "compute_novav2_url";
    private static final String COMPUTE_NOVAV2_NETWORK_KEY = "compute_novav2_network_id";

    private OpenStackNovaV2ComputePlugin novaV2ComputeOpenStack;
    private Token localToken;
    private ComputeOrder computeOrder;
    private LaunchCommandGenerator launchCommandGenerator;

    @Before
    public void setUp() throws Exception {
        Properties properties = new Properties();
        properties.put(COMPUTE_NOVAV2_URL_KEY, FAKE_ENDPOINT);
        properties.put(COMPUTE_NOVAV2_NETWORK_KEY, FAKE_NET_ID);

        this.localToken = mock(Token.class);

		computeOrder = new ComputeOrder(localToken, null, null, null, 1, 2000, 20, null,
				new UserData(FAKE_USER_DATA_FILE, CloudInitUserDataBuilder.FileType.SHELL_SCRIPT), null);
		
		launchCommandGenerator = mock(LaunchCommandGenerator.class);

        novaV2ComputeOpenStack = spy(new OpenStackNovaV2ComputePlugin(properties, launchCommandGenerator));
    }

    @Test
    public void testRequestInstance() throws IOException, RequestException {
        Flavor flavor = mock(Flavor.class);
        doReturn(flavor).when(novaV2ComputeOpenStack).findSmallestFlavor(any(ComputeOrder.class));
        
        String fakeCommand = "fake-command"; 
        doReturn(fakeCommand).when(launchCommandGenerator).createLaunchCommand(any(ComputeOrder.class));

        doReturn(FAKE_ENDPOINT).when(novaV2ComputeOpenStack).getComputeEndpoint(anyString(), anyString());

        JSONObject json = novaV2ComputeOpenStack.generateJsonRequest(FAKE_IMAGE_ID, FAKE_FLAVOR_ID, FAKE_USER_DATA_FILE,
                FAKE_KEYNAME, FAKE_NET_ID);
        doReturn(json).when(novaV2ComputeOpenStack)
                .generateJsonRequest(anyString(), anyString(), anyString(), anyString(), anyString());

        doReturn(FAKE_POST_RETURN).when(novaV2ComputeOpenStack).
                doPostRequest(eq(FAKE_ENDPOINT), eq(localToken), eq(json));

        String instanceId = novaV2ComputeOpenStack.requestInstance(computeOrder, FAKE_IMAGE_ID);
        assertEquals(instanceId, FAKE_INSTANCE_ID);
    }

    @Test(expected = RequestException.class)
    public void testRequestInstanceIrregularSyntax() throws IOException, RequestException {
        Flavor flavor = mock(Flavor.class);
        doReturn(flavor).when(novaV2ComputeOpenStack).findSmallestFlavor(any(ComputeOrder.class));
        
        String fakeCommand = "fake-command"; 
        doReturn(fakeCommand).when(launchCommandGenerator).createLaunchCommand(any(ComputeOrder.class));

        doReturn(INVALID_FAKE_POST_RETURN).when(novaV2ComputeOpenStack).
                doPostRequest(anyString(), any(Token.class), any(JSONObject.class));

        novaV2ComputeOpenStack.requestInstance(computeOrder, FAKE_IMAGE_ID);
    }

    @Test
    public void testGenerateJsonRequest() {
        JSONObject json = novaV2ComputeOpenStack.generateJsonRequest("imageRef",
                "flavorRef", "user-data", "keyName", "netId");

        assertNotNull(json);
        JSONObject server = json.getJSONObject("server");
        assertNotNull(server);

        String name = server.getString("name");
        assertNotNull(name);

        String image = server.getString("imageRef");
        assertEquals("imageRef", image);

        String key = server.getString("key_name");
        assertEquals("keyName", key);

        String flavor = server.getString("flavorRef");
        assertEquals("flavorRef", flavor);

        String user = server.getString("user_data");
        assertEquals("user-data", user);

        JSONArray net = server.getJSONArray("networks");
        assertNotNull(net);
        assertEquals(1, net.length());
        assertEquals("netId", net.getJSONObject(0).getString("uuid"));
    }

    @Test(expected = JSONException.class)
    public void testGenerateJsonRequestWithoutUserData() {
        JSONObject json = novaV2ComputeOpenStack.generateJsonRequest("imageRef",
                "flavorRef", null, "keyName", "netId");

        assertNotNull(json);
        JSONObject server = json.getJSONObject("server");
        assertNotNull(server);

        String name = server.getString("name");
        assertNotNull(name);

        String image = server.getString("imageRef");
        assertEquals("imageRef", image);

        String key = server.getString("key_name");
        assertEquals("keyName", key);

        String flavor = server.getString("flavorRef");
        assertEquals("flavorRef", flavor);

        JSONArray net = server.getJSONArray("networks");
        assertNotNull(net);
        assertEquals(1, net.length());
        assertEquals("netId", net.getJSONObject(0).getString("uuid"));

        server.get("user_data");
    }

    @Test(expected = JSONException.class)
    public void testGenerateJsonRequestWithoutNetwork() {
        JSONObject json = novaV2ComputeOpenStack.generateJsonRequest("imageRef",
                "flavorRef", "user-data", "keyName", null);

        assertNotNull(json);
        JSONObject server = json.getJSONObject("server");
        assertNotNull(server);

        String name = server.getString("name");
        assertNotNull(name);

        String image = server.getString("imageRef");
        assertEquals("imageRef", image);

        String key = server.getString("key_name");
        assertEquals("keyName", key);

        String flavor = server.getString("flavorRef");
        assertEquals("flavorRef", flavor);

        server.getJSONArray("networks");
    }

    @Test(expected = JSONException.class)
    public void testGenerateJsonRequestWithoutKeyName() {
        JSONObject json = novaV2ComputeOpenStack.generateJsonRequest("imageRef",
                "flavorRef", "user-data", null, "netId");

        assertNotNull(json);
        JSONObject server = json.getJSONObject("server");
        assertNotNull(server);

        String name = server.getString("name");
        assertNotNull(name);

        String image = server.getString("imageRef");
        assertEquals("imageRef", image);

        String flavor = server.getString("flavorRef");
        assertEquals("flavorRef", flavor);

        JSONArray net = server.getJSONArray("networks");
        assertNotNull(net);
        assertEquals(1, net.length());
        assertEquals("netId", net.getJSONObject(0).getString("uuid"));

        server.getString("key_name");
    }

    @Test
    public void testGetActivateInstance() throws RequestException {
        doReturn(FAKE_ENDPOINT).when(novaV2ComputeOpenStack).getComputeEndpoint(anyString(), anyString());

        doReturn(FAKE_GET_RETURN_ACTIVE_INSTANCE).when(novaV2ComputeOpenStack)
                .doGetRequest(eq(FAKE_ENDPOINT), eq(localToken));

        ComputeOrderInstance computeOrderInstance = this.novaV2ComputeOpenStack.
                getInstance(this.localToken, FAKE_INSTANCE_ID);

        assertEquals(computeOrderInstance.getId(), FAKE_INSTANCE_ID);
        assertEquals(computeOrderInstance.getState(), InstanceState.ACTIVE);
    }

    @Test
    public void testGetFailedInstance() throws RequestException {
        doReturn(FAKE_ENDPOINT).when(novaV2ComputeOpenStack).getComputeEndpoint(anyString(), anyString());

        doReturn(FAKE_GET_RETURN_FAILED_INSTANCE).when(novaV2ComputeOpenStack)
                .doGetRequest(eq(FAKE_ENDPOINT), eq(localToken));

        ComputeOrderInstance computeOrderInstance = this.novaV2ComputeOpenStack.
                getInstance(this.localToken, FAKE_INSTANCE_ID);

        assertEquals(computeOrderInstance.getId(), FAKE_INSTANCE_ID);
        assertEquals(computeOrderInstance.getState(), InstanceState.FAILED);
    }

    @Test
    public void testGetInactiveInstance() throws RequestException {
        doReturn(FAKE_ENDPOINT).when(novaV2ComputeOpenStack).getComputeEndpoint(anyString(), anyString());

        doReturn(FAKE_GET_RETURN_INACTIVE_INSTANCE).when(novaV2ComputeOpenStack)
                .doGetRequest(eq(FAKE_ENDPOINT), eq(localToken));

        ComputeOrderInstance computeOrderInstance = this.novaV2ComputeOpenStack.
                getInstance(this.localToken, FAKE_INSTANCE_ID);

        assertEquals(computeOrderInstance.getId(), FAKE_INSTANCE_ID);
        assertEquals(computeOrderInstance.getState(), InstanceState.INACTIVE);
    }

    @Test(expected = Exception.class)
    public void testGetInstanceWithJSONException() throws RequestException {
        doReturn(INVALID_FAKE_POST_RETURN).when(novaV2ComputeOpenStack).doGetRequest(anyString(), any(Token.class));

        this.novaV2ComputeOpenStack.getInstance(this.localToken, FAKE_INSTANCE_ID);
    }
}
