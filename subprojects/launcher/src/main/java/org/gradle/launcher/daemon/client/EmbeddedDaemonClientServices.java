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

import org.gradle.initialization.BuildLayoutParameters;
import org.gradle.initialization.GradleLauncherFactory;
import org.gradle.internal.Factory;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.nativeplatform.ProcessEnvironment;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.scopes.GlobalScopeServices;
import org.gradle.launcher.daemon.configuration.DaemonParameters;
import org.gradle.launcher.daemon.context.DaemonContext;
import org.gradle.launcher.daemon.context.DaemonContextBuilder;
import org.gradle.launcher.daemon.registry.DaemonDir;
import org.gradle.launcher.daemon.registry.DaemonRegistry;
import org.gradle.launcher.daemon.registry.EmbeddedDaemonRegistry;
import org.gradle.launcher.daemon.server.Daemon;
import org.gradle.launcher.daemon.server.DaemonServerConnector;
import org.gradle.launcher.daemon.server.DaemonTcpServerConnector;
import org.gradle.launcher.daemon.server.exec.DaemonCommandExecuter;
import org.gradle.launcher.daemon.server.exec.DefaultDaemonCommandExecuter;
import org.gradle.launcher.daemon.server.exec.NoOpDaemonCommandAction;
import org.gradle.logging.LoggingManagerInternal;
import org.gradle.logging.LoggingServiceRegistry;
import org.gradle.logging.internal.OutputEvent;
import org.gradle.logging.internal.OutputEventListener;

import java.io.File;
import java.util.UUID;

/**
 * Wires together the embedded daemon client.
 */
public class EmbeddedDaemonClientServices extends DaemonClientServicesSupport {
    public EmbeddedDaemonClientServices() {
        this(LoggingServiceRegistry.newProcessLogging());
    }

    private class EmbeddedDaemonFactory implements Factory<Daemon> {
        public Daemon create() {
            return new Daemon(
                get(DaemonServerConnector.class),
                get(DaemonRegistry.class),
                get(DaemonContext.class),
                "password",
                get(DaemonCommandExecuter.class),
                get(ExecutorFactory.class)
            );
        }
    }

    protected DaemonCommandExecuter createDaemonCommandExecuter() {
        LoggingManagerInternal mgr = newInstance(LoggingManagerInternal.class);
        return new DefaultDaemonCommandExecuter(get(GradleLauncherFactory.class),
                get(ProcessEnvironment.class), mgr, new File("dummy"), new NoOpDaemonCommandAction());
    }

    public EmbeddedDaemonClientServices(ServiceRegistry loggingServices) {
        super(loggingServices, System.in);
        addProvider(new GlobalScopeServices(false));
        add(EmbeddedDaemonFactory.class, new EmbeddedDaemonFactory());
    }

    protected DaemonRegistry createDaemonRegistry() {
        return new EmbeddedDaemonRegistry();
    }

    protected OutputEventListener createOutputEventListener() {
        return new OutputEventListener() { public void onOutput(OutputEvent event) {} };
    }

    @Override
    protected void configureDaemonContextBuilder(DaemonContextBuilder builder) {
        builder.setUid(UUID.randomUUID().toString());
        builder.setDaemonRegistryDir(new DaemonDir(new DaemonParameters(new BuildLayoutParameters()).getBaseDir()).getRegistry());
    }

    protected DaemonServerConnector createDaemonServerConnector() {
        return new DaemonTcpServerConnector();
    }

    protected DaemonStarter createDaemonStarter() {
        return new EmbeddedDaemonStarter(getFactory(Daemon.class));
    }
}
