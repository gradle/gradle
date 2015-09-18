package org.gradle.api.plugins.quality.internal.forking.next;

import org.gradle.StartParameter;
import org.gradle.internal.Factory;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.scopes.PluginServiceRegistry;
import org.gradle.process.internal.WorkerProcessBuilder;


public class AntWorkerServiceRegistry implements PluginServiceRegistry {
    @Override
    public void registerGlobalServices(ServiceRegistration registration) {
    }

    @Override
    public void registerBuildSessionServices(ServiceRegistration registration) {
        registration.addProvider(new BuildSessionScopeAntWorkerServices());
    }

    @Override
    public void registerBuildServices(ServiceRegistration registration) {
    }

    @Override
    public void registerGradleServices(ServiceRegistration registration) {
    }

    @Override
    public void registerProjectServices(ServiceRegistration registration) {
    }

    private static class BuildSessionScopeAntWorkerServices {
        AntWorkerDaemonManager createAntWorkerDaemonManager(Factory<WorkerProcessBuilder> workerFactory, StartParameter startParameter) {
            return new AntWorkerDaemonManager(new AntWorkerClientsManager(new AntWorkerDaemonStarter(workerFactory, startParameter)));
        }
    }
}
