package org.fogbowcloud.ras.core.plugins.aaa.tokengenerator;

import org.fogbowcloud.ras.core.exceptions.FatalErrorException;
import org.fogbowcloud.ras.core.exceptions.FogbowRasException;
import org.fogbowcloud.ras.core.exceptions.UnexpectedException;
import org.fogbowcloud.ras.util.RSAUtil;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.interfaces.RSAPublicKey;
import java.util.Map;

public class TokenGeneratorPluginProtectionWrapper implements TokenGeneratorPlugin {
    public static final String SEPARATOR = "!#!";
    private TokenGeneratorPlugin embeddedPlugin;
    private RSAPublicKey publicKey;

    public TokenGeneratorPluginProtectionWrapper(TokenGeneratorPlugin embeddedPlugin) {
        this.embeddedPlugin = embeddedPlugin;
        try {
            this.publicKey = RSAUtil.getPublicKey();
        } catch (IOException | GeneralSecurityException e) {
            throw new FatalErrorException();
        }
    }

    @Override
    public String createTokenValue(Map<String, String> userCredentials) throws UnexpectedException, FogbowRasException {
        String unprotectedTokenValue = this.embeddedPlugin.createTokenValue(userCredentials);
        return encrypt(unprotectedTokenValue);
    }

    public TokenGeneratorPlugin getEmbeddedPlugin() {
        return this.embeddedPlugin;
    }

    private String encrypt(String unprotectedTokenValue) throws UnexpectedException {
        String randomKey;
        String protectedTokenValue;
        String protectedKey;
        try {
            randomKey = RSAUtil.generateAESKey();
            protectedTokenValue = RSAUtil.encryptAES(randomKey.getBytes("UTF-8"), unprotectedTokenValue);
            protectedKey = RSAUtil.encrypt(randomKey, this.publicKey);
            return protectedKey + SEPARATOR + protectedTokenValue;
        } catch (Exception e) {
            throw new UnexpectedException();
        }
    }
}
