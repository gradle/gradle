/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.launcher.daemon.client;

import org.gradle.api.specs.Spec;
import org.gradle.configuration.GradleLauncherMetaData;
import org.gradle.launcher.daemon.context.DaemonContext;
import org.gradle.launcher.daemon.context.DaemonContextBuilder;
import org.gradle.launcher.daemon.context.DaemonCompatibilitySpec;
import org.gradle.launcher.daemon.registry.DaemonRegistry;
import org.gradle.messaging.remote.internal.OutgoingConnector;
import org.gradle.messaging.remote.internal.inet.TcpOutgoingConnector;
import org.gradle.messaging.remote.internal.DefaultMessageSerializer;
import org.gradle.logging.internal.OutputEventListener;
import org.gradle.api.internal.project.DefaultServiceRegistry;
import org.gradle.api.internal.project.ServiceRegistry;
import org.gradle.initialization.BuildClientMetaData;

/**
 * Some support wiring for daemon clients.
 * 
 * @see DaemonClientServices
 * @see EmbeddedDaemonClientServices
 */
abstract public class DaemonClientServicesSupport extends DefaultServiceRegistry {

    private final ServiceRegistry loggingServices;

    public DaemonClientServicesSupport(ServiceRegistry loggingServices) {
        this.loggingServices = loggingServices;
    }

    public ServiceRegistry getLoggingServices() {
        return loggingServices;
    }

    protected DaemonContext createDaemonContext() {
        DaemonContextBuilder builder = new DaemonContextBuilder();
        configureDaemonContextBuilder(builder);
        return builder.create();
    }

    // subclass hook, allowing us to fake the context for testing
    protected void configureDaemonContextBuilder(DaemonContextBuilder builder) {
        
    }
    
    // not following convention because I don't want to name this method createSpec()
    public Spec<DaemonContext> makeDaemonCompatibilitySpec() {
        return new DaemonCompatibilitySpec(get(DaemonContext.class));
    }

    protected DaemonClient createDaemonClient() {
        return new DaemonClient(get(DaemonConnector.class), get(BuildClientMetaData.class), get(OutputEventListener.class));
    }

    protected OutputEventListener createOutputEventListener() {
        return getLoggingServices().get(OutputEventListener.class);
    }

    protected BuildClientMetaData createBuildClientMetaData() {
        return new GradleLauncherMetaData();
    }

    protected OutgoingConnector<Object> createOutgoingConnector() {
        return new TcpOutgoingConnector<Object>(new DefaultMessageSerializer<Object>(getClass().getClassLoader()));
    }

    @SuppressWarnings("unchecked")
    protected DaemonConnector createDaemonConnector() {
        return new DefaultDaemonConnector(get(DaemonRegistry.class), makeDaemonCompatibilitySpec(), get(OutgoingConnector.class), makeDaemonStarter());
    }

    abstract protected DaemonRegistry createDaemonRegistry();
    
    abstract Runnable makeDaemonStarter();
}