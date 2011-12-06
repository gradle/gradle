package org.gradle.tooling.internal.consumer;

import org.gradle.StartParameter;
import org.gradle.api.internal.project.DefaultServiceRegistry;
import org.gradle.api.internal.project.ServiceRegistry;
import org.gradle.listener.DefaultListenerManager;
import org.gradle.listener.ListenerManager;
import org.gradle.logging.ProgressLoggerFactory;
import org.gradle.logging.internal.DefaultProgressLoggerFactory;
import org.gradle.logging.internal.ProgressListener;
import org.gradle.tooling.GradleConnector;
import org.gradle.util.TrueTimeProvider;

/**
 * by Szczepan Faber, created at: 12/6/11
 */
public class ConnectorServices {

    private static final ToolingImplementationLoader TOOLING_API_LOADER = new CachingToolingImplementationLoader(new DefaultToolingImplementationLoader());

    public GradleConnector createConnector() {
        //TODO I'd like to add some coverage documenting what core instances are singletons
        //(e.g. to document knowledge like 'single DefaultConnection per distro' etc.
        ServiceRegistry services = new ConnectorServiceRegistry();
        return new DefaultGradleConnector(services.get(ConnectionFactory.class), services.get(DistributionFactory.class));
    }

    private class ConnectorServiceRegistry extends DefaultServiceRegistry {

        protected ListenerManager createListenerManager() {
            return new DefaultListenerManager();
        }

        protected ProgressLoggerFactory createProgressLoggerFactory() {
            return new DefaultProgressLoggerFactory(get(ListenerManager.class).getBroadcaster(ProgressListener.class), new TrueTimeProvider());
        }

        protected ConnectionFactory createConnectionFactory() {
            return new ConnectionFactory(TOOLING_API_LOADER, get(ListenerManager.class), get(ProgressLoggerFactory.class));
        }

        protected DistributionFactory createDistributionFactory() {
            ProgressLoggerFactory progressLoggerFactory = get(ProgressLoggerFactory.class);
            return new DistributionFactory(StartParameter.DEFAULT_GRADLE_USER_HOME, progressLoggerFactory);
        }
    }
}
