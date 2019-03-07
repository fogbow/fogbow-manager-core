package cloud.fogbow.ras.core.stubs;

import cloud.fogbow.common.models.CloudUser;
import cloud.fogbow.ras.api.http.response.NetworkInstance;
import cloud.fogbow.ras.core.models.orders.NetworkOrder;
import cloud.fogbow.ras.core.plugins.interoperability.NetworkPlugin;

/**
 * This class is a stub for the NetworkPlugin interface used for tests only.
 * Should not have a proper implementation.
 */
public class StubNetworkPlugin implements NetworkPlugin<CloudUser> {

    public StubNetworkPlugin(String confFilePath) {
    }

    @Override
    public String requestInstance(NetworkOrder networkOrder, CloudUser cloudUser) {
        return null;
    }

    @Override
    public NetworkInstance getInstance(String networkInstanceId, CloudUser cloudUser) {
        return null;
    }

    @Override
    public void deleteInstance(String networkInstanceId, CloudUser cloudUser) {
    }
}
