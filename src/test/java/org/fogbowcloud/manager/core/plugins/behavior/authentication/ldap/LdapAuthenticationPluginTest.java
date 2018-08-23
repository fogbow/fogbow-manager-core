package org.fogbowcloud.manager.core.plugins.behavior.authentication.ldap;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.fogbowcloud.manager.core.HomeDir;
import org.fogbowcloud.manager.core.models.tokens.FederationUserToken;
import org.fogbowcloud.manager.core.models.tokens.generators.ldap.LdapTokenGeneratorPlugin;
import org.fogbowcloud.manager.core.plugins.behavior.identity.ldap.LdapFederationIdentityPlugin;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class LdapAuthenticationPluginTest {

    private LdapAuthenticationPlugin authenticationPlugin;
    private Map<String, String> userCredentials;
    private String name;
    private String password;

    @Before
    public void setUp() {
        HomeDir homeDir = HomeDir.getInstance();
        homeDir.setPath("src/test/resources/private");

        this.authenticationPlugin = Mockito.spy(new LdapAuthenticationPlugin());

        this.name = "ldapUser";
        this.password = "ldapUserPass";

        this.userCredentials = new HashMap<String, String>();
        this.userCredentials.put(LdapTokenGeneratorPlugin.CRED_USERNAME, this.name);
        this.userCredentials.put(LdapTokenGeneratorPlugin.CRED_PASSWORD, this.password);
        this.userCredentials.put(LdapTokenGeneratorPlugin.CRED_AUTH_URL, "ldapUrl");
        this.userCredentials.put(LdapTokenGeneratorPlugin.CRED_LDAP_BASE, "ldapBase");
        this.userCredentials.put(LdapTokenGeneratorPlugin.CRED_LDAP_ENCRYPT, "");
        this.userCredentials.put(LdapTokenGeneratorPlugin.CRED_PRIVATE_KEY, "private_key_path");
        this.userCredentials.put(LdapTokenGeneratorPlugin.CRED_PUBLIC_KEY, "public_key_path");
    }

    //test case: check if isAuthentic returns true when the tokenValue is not expired.
    @Test
    public void testGetTokenValidTokenValue() throws Exception {
        //set up
        LdapFederationIdentityPlugin identityPlugin = Mockito.spy(new LdapFederationIdentityPlugin());
        LdapTokenGeneratorPlugin tokenGenerator = Mockito.spy(new LdapTokenGeneratorPlugin());
        Mockito.doReturn(this.name).when(tokenGenerator).ldapAuthenticate(Mockito.anyString(), Mockito.anyString());
        Mockito.doReturn(true).when(this.authenticationPlugin).verifySign(Mockito.anyString(),
                Mockito.anyString());

        //exercise
        String tokenValue = tokenGenerator.createTokenValue(this.userCredentials);
        FederationUserToken token = identityPlugin.createToken(tokenValue);

        //verify
        Assert.assertTrue(this.authenticationPlugin.isAuthentic(token));
    }

    //test case: check if isAuthentic returns false when the tokenValue is invalid (signature doesn't match).
    @Test
    public void testGetTokenExpiredTokenValue() throws Exception {
        //set up
        LdapFederationIdentityPlugin identityPlugin = Mockito.spy(new LdapFederationIdentityPlugin());
        LdapTokenGeneratorPlugin tokenGenerator = Mockito.spy(new LdapTokenGeneratorPlugin());
        Mockito.doReturn(this.name).when(tokenGenerator).ldapAuthenticate(Mockito.anyString(), Mockito.anyString());

        //exercise
        String tokenValue = tokenGenerator.createTokenValue(this.userCredentials);
        String split[] = tokenValue.split(LdapTokenGeneratorPlugin.TOKEN_VALUE_SEPARATOR);
        String newTokenValue = split[0] + LdapTokenGeneratorPlugin.TOKEN_VALUE_SEPARATOR + "another user id" +
                LdapTokenGeneratorPlugin.TOKEN_VALUE_SEPARATOR + split[2] +
                LdapTokenGeneratorPlugin.TOKEN_VALUE_SEPARATOR + split[3] +
                LdapTokenGeneratorPlugin.TOKEN_VALUE_SEPARATOR + split[4];
        FederationUserToken newToken = identityPlugin.createToken(newTokenValue);

        //verify
        Assert.assertFalse(this.authenticationPlugin.isAuthentic(newToken));
    }

    //test case: check if isAuthentic returns false when the tokenValue is expired.
    @Test
    public void testGetTokenInvalidTokenValue() throws Exception {
        //set up
        LdapFederationIdentityPlugin identityPlugin = Mockito.spy(new LdapFederationIdentityPlugin());
        LdapTokenGeneratorPlugin tokenGenerator = Mockito.spy(new LdapTokenGeneratorPlugin());
        Mockito.doReturn(this.name).when(tokenGenerator).ldapAuthenticate(Mockito.anyString(), Mockito.anyString());

        //exercise
        String tokenValue = tokenGenerator.createTokenValue(this.userCredentials);
        String split[] = tokenValue.split(LdapTokenGeneratorPlugin.TOKEN_VALUE_SEPARATOR);
        String newExpirationTime = Long.toString((new Date().getTime()));
        String newTokenValue = split[0] + LdapTokenGeneratorPlugin.TOKEN_VALUE_SEPARATOR + split[1] +
                LdapTokenGeneratorPlugin.TOKEN_VALUE_SEPARATOR + newExpirationTime
                + LdapTokenGeneratorPlugin.TOKEN_VALUE_SEPARATOR + split[3]
                + LdapTokenGeneratorPlugin.TOKEN_VALUE_SEPARATOR + split[4];
        FederationUserToken newToken = identityPlugin.createToken(newTokenValue);

        //verify
        Assert.assertFalse(this.authenticationPlugin.isAuthentic(newToken));
    }
}
