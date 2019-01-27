package cloud.fogbow.ras.core.plugins.aaa.tokengenerator.cloudstack;

import cloud.fogbow.common.constants.CloudStackRestApiConstants;
import cloud.fogbow.ras.core.plugins.interoperability.cloudstack.CloudStackUrlMatcher;
import org.apache.http.client.HttpResponseException;
import cloud.fogbow.ras.core.PropertiesHolder;
import cloud.fogbow.ras.core.constants.ConfigurationConstants;
import cloud.fogbow.ras.core.constants.SystemConstants;
import org.fogbowcloud.ras.core.models.tokens.CloudStackToken;
import org.fogbowcloud.ras.core.models.tokens.Token;
import org.fogbowcloud.ras.core.plugins.aaa.authentication.cloudstack.CloudStackAuthenticationPlugin;
import org.fogbowcloud.ras.core.plugins.aaa.identity.cloudstack.CloudStackIdentityPlugin;
import cloud.fogbow.ras.core.plugins.interoperability.cloudstack.CloudStackUrlUtil;
import cloud.fogbow.ras.util.connectivity.AuditableHttpRequestClient;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@RunWith(PowerMockRunner.class)
@PrepareForTest({CloudStackUrlUtil.class, HttpRequestUtil.class})
public class CloudStackTokenGeneratorPluginTest {
    private static final String FAKE_ID = "fake-id";
    private static final String FAKE_FIRST_NAME = "fake-first-name";
    private static final String FAKE_LAST_NAME = "fake-last-name";
    private static final String FAKE_USERNAME = "fake-username";
    private static final String FAKE_FULL_USERNAME = FAKE_FIRST_NAME + " " + FAKE_LAST_NAME;
    private static final String FAKE_PASSWORD = "fake-password";
    private static final String FAKE_DOMAIN = "fake-domain";
    private static final String FAKE_SESSION_KEY = "fake-session-key";
    private static final String FAKE_TIMEOUT = "fake-timeout";
    private static final String JSON = "json";

    private static final String COMMAND_KEY = "command";
    private static final String RESPONSE_KEY = "response";
    private static final String USERNAME_KEY = "username";
    private static final String PASSWORD_KEY = "password";
    private static final String DOMAIN_KEY = "domain";
    private static final String SESSION_KEY_KEY = "sessionkey";
    private static final String CLOUDSTACK_URL = "cloudstack_api_url";
    private static final String CLOUD_NAME = "cloudstack";

    private static final String FAKE_API_KEY = "fake-api-key";
    private static final String FAKE_SECRET_KEY = "fake-secret-key";
    private static final String FAKE_TOKEN_VALUE = FAKE_API_KEY + CloudStackRestApiConstants.KEY_VALUE_SEPARATOR + FAKE_SECRET_KEY;

    private AuditableHttpRequestClient auditableHttpRequestClient;
    private CloudStackTokenGeneratorPlugin cloudStackTokenGenerator;
    private CloudStackIdentityPlugin cloudStackIdentityPlugin;
    private CloudStackAuthenticationPlugin cloudStackAuthenticationPlugin;
    private Properties properties;
    private String memberId;

    @Before
    public void setUp() throws Exception {
        PowerMockito.mockStatic(HttpRequestUtil.class);

        String cloudStackConfFilePath = HomeDir.getPath() + SystemConstants.CLOUDS_CONFIGURATION_DIRECTORY_NAME +
                File.separator + CLOUD_NAME + File.separator + SystemConstants.CLOUD_SPECIFICITY_CONF_FILE_NAME;

        this.properties = PropertiesUtil.readProperties(cloudStackConfFilePath);
        this.auditableHttpRequestClient = Mockito.mock(AuditableHttpRequestClient.class);
        this.cloudStackTokenGenerator = Mockito.spy(new CloudStackTokenGeneratorPlugin(cloudStackConfFilePath));
        this.cloudStackTokenGenerator.setClient(this.auditableHttpRequestClient);
        this.cloudStackIdentityPlugin = new CloudStackIdentityPlugin();
        this.memberId = PropertiesHolder.getInstance().getProperty(ConfigurationConstants.LOCAL_MEMBER_ID_KEY);
        this.cloudStackAuthenticationPlugin = new CloudStackAuthenticationPlugin(this.memberId);
    }

    // Test case: when creating a token, two requests should be made: one post request to login the user using their
    // credentials and get a session key to perform the other get request to retrieve the user "token", i.e., info
    // needed to perform requests in cloudstack (namely api key and secret key)
    @Test
    public void testCreateToken() throws FogbowRasException, UnexpectedException, IOException {
        //set up
        String endpoint = getBaseEndpointFromCloudStackConf();
        String listAccountsCommand = ListAccountsRequest.LIST_ACCOUNTS_COMMAND;
        String loginCommand = LoginRequest.LOGIN_COMMAND;

        String loginJsonResponse = getLoginResponse(FAKE_SESSION_KEY, FAKE_TIMEOUT);
        String accountJsonResponse = getAccountResponse(FAKE_ID, FAKE_USERNAME, FAKE_FIRST_NAME, FAKE_LAST_NAME,
                FAKE_API_KEY, FAKE_SECRET_KEY);
        String expectedListAccountsRequestUrl = generateExpectedUrl(endpoint, listAccountsCommand,
                RESPONSE_KEY, JSON,
                SESSION_KEY_KEY, FAKE_SESSION_KEY);

        Map<String, String> expectedParams = new HashMap<>();
        expectedParams.put(COMMAND_KEY, loginCommand);
        expectedParams.put(RESPONSE_KEY, JSON);
        expectedParams.put(USERNAME_KEY, FAKE_USERNAME);
        expectedParams.put(PASSWORD_KEY, FAKE_PASSWORD);
        expectedParams.put(DOMAIN_KEY, FAKE_DOMAIN);
        CloudStackUrlMatcher urlMatcher = new CloudStackUrlMatcher(expectedParams);

        PowerMockito.mockStatic(CloudStackUrlUtil.class);
        PowerMockito.when(CloudStackUrlUtil.createURIBuilder(Mockito.anyString(), Mockito.anyString())).thenCallRealMethod();

        AuditableHttpRequestClient.Response httpResponse = Mockito.mock(AuditableHttpRequestClient.Response.class);
        Mockito.when(httpResponse.getContent()).thenReturn(loginJsonResponse);
        Mockito.when(this.auditableHttpRequestClient.doPostRequest(Mockito.argThat(urlMatcher), Mockito.anyString()))
                .thenReturn(httpResponse);
        Mockito.when(this.auditableHttpRequestClient.doGetRequest(Mockito.eq(expectedListAccountsRequestUrl), Mockito.any(Token.class)))
                .thenReturn(accountJsonResponse);

        Map<String, String> userCredentials = new HashMap<String, String>();
        userCredentials.put(CloudStackTokenGeneratorPlugin.USERNAME, FAKE_USERNAME);
        userCredentials.put(CloudStackTokenGeneratorPlugin.PASSWORD, FAKE_PASSWORD);
        userCredentials.put(CloudStackTokenGeneratorPlugin.DOMAIN, FAKE_DOMAIN);

        //exercise
        String tokenValue = this.cloudStackTokenGenerator.createTokenValue(userCredentials);
        CloudStackToken token = this.cloudStackIdentityPlugin.createToken(tokenValue);

        //verify
        String split[] = tokenValue.split(CloudStackTokenGeneratorPlugin.CLOUDSTACK_TOKEN_STRING_SEPARATOR);
        Assert.assertEquals(split.length, CloudStackTokenGeneratorPlugin.CLOUDSTACK_TOKEN_NUMBER_OF_FIELDS);
        Assert.assertEquals(split[0], memberId);
        Assert.assertEquals(split[1], FAKE_TOKEN_VALUE);
        Assert.assertEquals(split[2], FAKE_ID);
        Assert.assertEquals(split[3], FAKE_FULL_USERNAME);
        Assert.assertTrue(this.cloudStackAuthenticationPlugin.isAuthentic(this.memberId, token));
    }

    // Test case: throw expection in case any of the credentials are invalid
    @Test(expected = FogbowRasException.class)
    public void testInvalidCredentials() throws FogbowRasException, UnexpectedException {
        Map<String, String> tokenAttributes = new HashMap<String, String>();
        tokenAttributes.put(CloudStackTokenGeneratorPlugin.USERNAME, null);
        tokenAttributes.put(CloudStackTokenGeneratorPlugin.PASSWORD, "key");
        tokenAttributes.put(CloudStackTokenGeneratorPlugin.DOMAIN, "domain");

        String token = this.cloudStackTokenGenerator.createTokenValue(tokenAttributes);
    }

    // Test case: http request fails on retrieving account details
    @Test(expected = FogbowRasException.class)
    public void testListAccountsFail() throws FogbowRasException, UnexpectedException, IOException {
        //set up
        String endpoint = getBaseEndpointFromCloudStackConf();
        String listAccountsCommand = ListAccountsRequest.LIST_ACCOUNTS_COMMAND;
        String loginCommand = LoginRequest.LOGIN_COMMAND;

        String loginJsonResponse = getLoginResponse(FAKE_SESSION_KEY, FAKE_TIMEOUT);
        String accountJsonResponse = getAccountResponse(FAKE_ID, FAKE_USERNAME, FAKE_FIRST_NAME, FAKE_LAST_NAME,
                FAKE_API_KEY, FAKE_SECRET_KEY);
        String expectedListAccountsRequestUrl = generateExpectedUrl(endpoint, listAccountsCommand,
                RESPONSE_KEY, JSON,
                SESSION_KEY_KEY, FAKE_SESSION_KEY);

        Map<String, String> expectedParams = new HashMap<>();
        expectedParams.put(COMMAND_KEY, loginCommand);
        expectedParams.put(RESPONSE_KEY, JSON);
        expectedParams.put(USERNAME_KEY, FAKE_USERNAME);
        expectedParams.put(PASSWORD_KEY, FAKE_PASSWORD);
        expectedParams.put(DOMAIN_KEY, FAKE_DOMAIN);
        CloudStackUrlMatcher urlMatcher = new CloudStackUrlMatcher(expectedParams);

        PowerMockito.mockStatic(CloudStackUrlUtil.class);
        PowerMockito.when(CloudStackUrlUtil.createURIBuilder(Mockito.anyString(), Mockito.anyString())).thenCallRealMethod();

        AuditableHttpRequestClient.Response httpResponse = Mockito.mock(AuditableHttpRequestClient.Response.class);
        Mockito.when(httpResponse.getContent()).thenReturn(loginJsonResponse);
        Mockito.when(this.auditableHttpRequestClient.doPostRequest(Mockito.argThat(urlMatcher), Mockito.anyString()))
                .thenReturn(httpResponse);
        Mockito.when(this.auditableHttpRequestClient.doGetRequest(Mockito.eq(expectedListAccountsRequestUrl), Mockito.any()))
                .thenThrow(new HttpResponseException(503, "service unavailable"));

        //exercise
        Map<String, String> userCredentials = new HashMap<String, String>();
        userCredentials.put(CloudStackTokenGeneratorPlugin.USERNAME, FAKE_USERNAME);
        userCredentials.put(CloudStackTokenGeneratorPlugin.PASSWORD, FAKE_PASSWORD);
        userCredentials.put(CloudStackTokenGeneratorPlugin.DOMAIN, FAKE_DOMAIN);

        String tokenValue = this.cloudStackTokenGenerator.createTokenValue(userCredentials);
    }

    private String getLoginResponse(String sessionKey, String timeout) {
        String response = "{\"loginresponse\":{"
                + "\"sessionkey\": \"%s\","
                + "\"timeout\": \"%s\""
                + "}}";

        return String.format(response, sessionKey, timeout);
    }

    private String getAccountResponse(String id, String username, String firstName, String lastName, String apiKey,
                                      String secretKey) {
        String response = "{\"listaccountsresponse\":{"
                + "\"account\":[{"
                + "\"user\":[{"
                + "\"id\": \"%s\","
                + "\"username\": \"%s\","
                + "\"firstname\": \"%s\","
                + "\"lastname\": \"%s\","
                + "\"apikey\": \"%s\","
                + "\"secretkey\": \"%s\""
                + "}]}]}}";

        return String.format(response, id, username, firstName, lastName, apiKey, secretKey);
    }

    private String generateExpectedUrl(String endpoint, String command, String... keysAndValues) {
        if (keysAndValues.length % 2 != 0) {
            // there should be one value for each key
            return null;
        }

        String url = String.format("%s?command=%s", endpoint, command);
        for (int i = 0; i < keysAndValues.length; i += 2) {
            String key = keysAndValues[i];
            String value = keysAndValues[i + 1];
            url += String.format("&%s=%s", key, value);
        }

        return url;
    }

    private String getBaseEndpointFromCloudStackConf() {
        return this.properties.getProperty(CLOUDSTACK_URL);
    }
}
