package cloud.fogbow.ras.core;

import cloud.fogbow.common.plugins.authorization.AuthorizationPlugin;
import cloud.fogbow.ras.core.models.RasOperation;

public class AuthorizationPluginInstantiator {
    private static RasClassFactory classFactory = new RasClassFactory();

    public static AuthorizationPlugin<RasOperation> getAuthorizationPlugin(String className) {
        return (AuthorizationPlugin<RasOperation>) AuthorizationPluginInstantiator.classFactory.createPluginInstance(className);
    }
}
