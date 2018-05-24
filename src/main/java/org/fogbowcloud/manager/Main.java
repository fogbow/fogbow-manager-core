package org.fogbowcloud.manager;

import java.util.Properties;
import org.fogbowcloud.manager.core.ApplicationFacade;
import org.fogbowcloud.manager.core.OrderController;
import org.fogbowcloud.manager.core.instanceprovider.LocalInstanceProvider;
import org.fogbowcloud.manager.core.instanceprovider.RemoteInstanceProvider;
import org.fogbowcloud.manager.core.manager.plugins.AuthorizationPlugin;
import org.fogbowcloud.manager.core.manager.plugins.IdentityPlugin;
import org.fogbowcloud.manager.core.manager.plugins.compute.ComputePlugin;
import org.fogbowcloud.manager.core.manager.plugins.network.NetworkPlugin;
import org.fogbowcloud.manager.core.manager.plugins.volume.VolumePlugin;
import org.fogbowcloud.manager.core.services.AAAController;
import org.fogbowcloud.manager.core.services.InstantiationInitService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class Main implements ApplicationRunner {

    private InstantiationInitService instantiationInitService;

    private Properties properties;

    private ApplicationFacade facade = ApplicationFacade.getInstance();
    private ProcessorController processorController;
    private AAAController aaaController;

    @Override
    public void run(ApplicationArguments args) {

        this.instantiationInitService = new InstantiationInitService();
        this.properties = this.instantiationInitService.getProperties();

        ComputePlugin computePlugin = this.instantiationInitService.getComputePlugin();
        NetworkPlugin networkPlugin = null;
        VolumePlugin volumePlugin = null;
        IdentityPlugin localIdentityPlugin = this.instantiationInitService.getLocalIdentityPlugin();
        IdentityPlugin federationIdentityPlugin =
                this.instantiationInitService.getFederationIdentityPlugin();
        AuthorizationPlugin authorizationPlugin =
                this.instantiationInitService.getAuthorizationPlugin();

        this.aaaController = new AAAController(federationIdentityPlugin, localIdentityPlugin,
                authorizationPlugin, this.properties);

        LocalInstanceProvider localInstanceProvider = new LocalInstanceProvider(computePlugin,
                networkPlugin, volumePlugin, this.aaaController);
        RemoteInstanceProvider remoteInstanceProvider = new RemoteInstanceProvider();

        this.processorController = new ProcessorController(this.properties, localInstanceProvider,
                remoteInstanceProvider, computePlugin, localIdentityPlugin,
                federationIdentityPlugin);

        this.facade.setAAAController(this.aaaController);
        this.facade.setOrderController(new OrderController(this.properties, localInstanceProvider,
                remoteInstanceProvider));
    }
}
