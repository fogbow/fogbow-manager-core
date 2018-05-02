package org.fogbowcloud.manager.core.services;

import org.apache.http.HttpStatus;
import org.fogbowcloud.manager.core.models.token.Token;
import org.fogbowcloud.manager.core.plugins.IdentityPlugin;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

public class AuthenticationServiceTest {

	AuthenticationService authenticationService;
	IdentityPlugin identityPlugin;

	@Before
	public void setUp() {
		this.identityPlugin = Mockito.mock(IdentityPlugin.class);
		this.authenticationService = new AuthenticationService(identityPlugin);
	}

	@Test
	public void testAuthenticateService() {
		Token token = new Token();
		Mockito.doReturn(token).when(identityPlugin).getToken(Mockito.anyString());
		Assert.assertEquals(token, authenticationService.authenticate(Mockito.anyString()));
	}

	@Test
	public void testInvalidAccessid() {
		Integer statusResponse = HttpStatus.SC_UNAUTHORIZED;
		Mockito.doThrow(new RuntimeException(statusResponse.toString())).when(identityPlugin)
				.getToken(Mockito.anyString());

		try {
			authenticationService.authenticate(Mockito.anyString());
			Assert.fail();
		} catch (RuntimeException runtimeException) {
			Assert.assertEquals(statusResponse.toString(), runtimeException.getMessage());
		}
	}

}
