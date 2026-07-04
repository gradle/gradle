/*
 * Copyright 2026 the original author or authors.
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
package org.gradle.launcher.daemon.connection;

import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.logging.console.GlobalUserInputReceiver;
import org.gradle.internal.logging.events.OutputEventListener;
import org.gradle.internal.nativeintegration.ProcessEnvironment;
import org.gradle.launcher.daemon.protocol.Build;
import org.gradle.launcher.exec.BuildActionResult;
import org.jspecify.annotations.NullMarked;

import java.io.InputStream;
import java.util.UUID;

/**
 * The {@link DaemonBuildExecuter} used when the client's JVM settings require a dedicated daemon: it forks a
 * single-use daemon, runs the build in it, and lets that daemon terminate afterwards.
 */
@NullMarked
public class SingleUseDaemonBuildExecuter extends AbstractDaemonBuildExecuter {
    public static final String MESSAGE = "To honour the JVM settings for this build a single-use Daemon process will be forked.";
    private static final Logger LOGGER = Logging.getLogger(SingleUseDaemonBuildExecuter.class);

    private final DocumentationRegistry documentationRegistry;

    public SingleUseDaemonBuildExecuter(
        DaemonConnector connector,
        OutputEventListener outputEventListener,
        InputStream buildStandardInput,
        GlobalUserInputReceiver userInput,
        IdGenerator<UUID> idGenerator,
        DocumentationRegistry documentationRegistry,
        ProcessEnvironment processEnvironment
    ) {
        super(connector, outputEventListener, buildStandardInput, userInput, idGenerator, processEnvironment);
        this.documentationRegistry = documentationRegistry;
    }

    @Override
    public BuildActionResult execute(DaemonBuildRequest request) {
        JvmBuildRequest jvmRequest = requireJvmRequest(request);
        LOGGER.lifecycle(MESSAGE + " {}", documentationRegistry.getDocumentationRecommendationFor("on this", "gradle_daemon", "sec:disabling_the_daemon"));

        DaemonClientConnection connection = getConnector().startSingleUseDaemon();
        Build build = newBuild(nextBuildId(), connection, jvmRequest);
        return executeBuild(build, connection, request.getCancellationToken(), request.getEventConsumer());
    }
}
