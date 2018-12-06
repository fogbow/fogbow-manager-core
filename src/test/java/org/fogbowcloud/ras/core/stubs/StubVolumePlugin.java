package org.fogbowcloud.ras.core.stubs;

import org.fogbowcloud.ras.core.models.instances.VolumeInstance;
import org.fogbowcloud.ras.core.models.orders.VolumeOrder;
import org.fogbowcloud.ras.core.models.tokens.Token;
import org.fogbowcloud.ras.core.plugins.interoperability.VolumePlugin;

/**
 * This class is allocationAllowableValues stub for the VolumePlugin interface used for tests only.
 * Should not have allocationAllowableValues proper implementation.
 */
public class StubVolumePlugin implements VolumePlugin<Token> {

    public StubVolumePlugin(String confFilePath) {
    }

    @Override
    public String requestInstance(VolumeOrder volumeOrder, Token token) {
        return null;
    }

    @Override
    public VolumeInstance getInstance(String volumeInstanceId, Token token) {
        return null;
    }

    @Override
    public void deleteInstance(String volumeInstanceId, Token token) {
    }
}
