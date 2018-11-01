package org.fogbowcloud.ras.core.plugins.aaa.authentication.opennebula;

import org.apache.commons.lang.StringUtils;
import org.fogbowcloud.ras.core.exceptions.UnexpectedException;
import org.fogbowcloud.ras.core.models.tokens.FederationUserToken;
import org.fogbowcloud.ras.core.models.tokens.OpenNebulaToken;
import org.fogbowcloud.ras.core.plugins.aaa.authentication.RASAuthenticationPlugin;
import org.fogbowcloud.ras.core.plugins.aaa.tokengenerator.cloudstack.CloudStackTokenGeneratorPlugin;
import org.fogbowcloud.ras.core.plugins.aaa.tokengenerator.opennebula.OpenNebulaTokenGeneratorPlugin;
import org.fogbowcloud.ras.core.plugins.interoperability.opennebula.OpenNebulaClientFactory;
import org.opennebula.client.Client;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.interfaces.RSAPublicKey;

public class OpenNebulaAuthenticationPlugin extends RASAuthenticationPlugin {
    @Override
    protected String getTokenMessage(FederationUserToken federationUserToken) {
        OpenNebulaToken oneToken = (OpenNebulaToken) federationUserToken;

        OpenNebulaClientFactory factory = new OpenNebulaClientFactory();

        // One more time, test if oneTokenValue is valid for any authentication.
        try {
            Client client = factory.createClient(oneToken.getTokenValue());
        } catch (UnexpectedException e) {
            System.out.println(e.getMessage());
        }


        String[] parameters = new String[]{
            oneToken.getTokenProvider(),
            oneToken.getTokenValue(),
            oneToken.getUserName(),
            oneToken.getSignature()
        };
        return StringUtils.join(parameters, OpenNebulaTokenGeneratorPlugin.OPENNEBULA_FIELD_SEPARATOR);
    }

    @Override
    protected String getSignature(FederationUserToken federationUserToken) {
        OpenNebulaToken oneToken = (OpenNebulaToken) federationUserToken;
        return oneToken.getSignature();
    }
}
