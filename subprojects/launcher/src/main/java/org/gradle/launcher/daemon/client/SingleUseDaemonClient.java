/*
 * Copyright 2012 the original author or authors.
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

import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.specs.ExplainingSpec;
import org.gradle.api.internal.specs.ExplainingSpecs;
import org.gradle.initialization.GradleLauncherAction;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.id.IdGenerator;
import org.gradle.launcher.daemon.context.DaemonContext;
import org.gradle.launcher.daemon.protocol.Build;
import org.gradle.launcher.daemon.protocol.BuildAndStop;
import org.gradle.launcher.exec.BuildActionParameters;
import org.gradle.logging.internal.OutputEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

public class SingleUseDaemonClient extends DaemonClient {
    private static final Logger LOGGER = LoggerFactory.getLogger(SingleUseDaemonClient.class);
    private final DocumentationRegistry documentationRegistry;

    public SingleUseDaemonClient(DaemonConnector connector, OutputEventListener outputEventListener, ExplainingSpec<DaemonContext> compatibilitySpec, InputStream buildStandardInput,
                                 ExecutorFactory executorFactory, IdGenerator<?> idGenerator, DocumentationRegistry documentationRegistry) {
        super(connector, outputEventListener, compatibilitySpec, buildStandardInput, executorFactory, idGenerator);
        this.documentationRegistry = documentationRegistry;
    }

    @Override
    public <T> T execute(GradleLauncherAction<T> action, BuildActionParameters parameters) {
        LOGGER.warn("Note: in order to honour the org.gradle.jvmargs and/or org.gradle.java.home values specified for this build, it is necessary to fork a new JVM.");
        LOGGER.warn("To avoid the slowdown associated with this extra process, you might want to consider running Gradle with the daemon enabled.");
        LOGGER.warn("Please see the user guide chapter on the daemon at {}.", documentationRegistry.getDocumentationFor("gradle_daemon"));
        Build build = new BuildAndStop(getIdGenerator().generateId(), action, parameters);

        DaemonConnection daemonConnection = getConnector().createConnection(ExplainingSpecs.<DaemonContext>satisfyAll());

        return (T) executeBuild(build, daemonConnection);
    }
}
