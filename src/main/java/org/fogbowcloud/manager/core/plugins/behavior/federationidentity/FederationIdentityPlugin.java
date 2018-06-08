package org.fogbowcloud.manager.core.plugins.behavior.federationidentity;

import java.util.Map;

import org.fogbowcloud.manager.core.exceptions.UnauthenticatedException;
import org.fogbowcloud.manager.core.models.token.FederationUser;
import org.fogbowcloud.manager.core.plugins.exceptions.TokenValueCreationException;

public interface FederationIdentityPlugin {

    /**
     * Creates a token value based on the user's credentials.
     *
     * @param userCredentials
     * @return a String that is a token value used to make requests
     * @throws UnauthenticatedException
     * @throws TokenValueCreationException
     */
    public String createFederationTokenValue(Map<String, String> userCredentials)
            throws UnauthenticatedException, TokenValueCreationException;

    /**
     * Based on an access id recreates a Token containing the corresponding access id plus the user
     * name and some arbitrary information regarding the token.
     *
     * @return a FederationUser for the corresponding federationTokenValue. A federationUser is
     * composed of a unique id (String) and a Map<String, String> of attributes.
     * Any implementation of this plugin MUST include at least the following attribute:
     * "user-name", which is the name of the user (ex. used in the Dashboard after authentication).
     * @throws UnauthenticatedException
     */
    public FederationUser getFederationUser(String federationTokenValue) throws UnauthenticatedException;

    /**
     * Verifies if the federationTokenValue is valid against the identity service.
     *
     * @param federationTokenValue
     * @return a boolean stating whether the token value is valid or not.
     */
    public boolean isValid(String federationTokenValue);

}
