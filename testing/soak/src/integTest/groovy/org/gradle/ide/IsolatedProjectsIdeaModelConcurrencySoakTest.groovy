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

package org.gradle.ide

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.daemon.DaemonLogsAnalyzer
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.internal.consumer.DefaultGradleConnector
import org.gradle.tooling.model.idea.IdeaProject

import java.util.concurrent.TimeUnit

/**
 * Soak test guarding against concurrent corruption of build-tree-scoped state shared while the IDE plugins
 * are applied to projects in parallel under Isolated Projects.
 *
 * Building the {@link IdeaProject} model under Isolated Projects fans the per-project model builders out in
 * parallel and applies {@code IdeaPlugin} to every project concurrently. Each application registers itself in
 * the build-tree-scoped {@code IdeArtifactStore}, which used to back its data with a non-thread-safe
 * {@code ArrayListMultimap}; concurrent writes corrupted a HashMap bin mid-treeification and produced an
 * intermittent {@code "java.util.HashMap$Node cannot be cast to java.util.HashMap$TreeNode"} during sync
 * (see {@code IsolatedProjectsGradleceptionSyncTest}).
 *
 * This is a probabilistic race, so it lives in the soak suite and is exercised with many projects (lots of
 * concurrent registrations, and HashMap bins large enough to treeify) over many fetches. The configuration
 * cache (implied by Isolated Projects) is invalidated each iteration so every fetch re-runs the parallel
 * model building rather than reusing a cached result.
 */
class IsolatedProjectsIdeaModelConcurrencySoakTest extends AbstractIntegrationSpec {

    private static final int PROJECT_COUNT = 200
    private static final int FETCH_ITERATIONS = 50

    private static final List<String> IP_PARALLEL = [
        "-Dorg.gradle.isolated-projects=true",
        "-Dorg.gradle.workers.max=16",
    ]

    def cleanup() {
        // killAll() blocks until each daemon process has actually exited (kill() alone returns before the
        // OS releases file handles), so the test directory can be deleted without racing a dying daemon.
        new DaemonLogsAnalyzer(file("daemon")).killAll()
    }

    def "concurrent IdeaProject model building does not corrupt shared build-tree state"() {
        given:
        settingsFile << """
            rootProject.name = "root"
            ${(1..PROJECT_COUNT).collect { "include('p$it')" }.join("\n")}
        """
        (1..PROJECT_COUNT).each { i ->
            file("p$i/build.gradle") << "plugins { id('java') }\n"
        }

        when:
        def connector = (GradleConnector.newConnector() as DefaultGradleConnector)
            .useInstallation(distribution.gradleHomeDir)
            .useGradleUserHomeDir(file("gradle-user-home"))
            .forProjectDirectory(testDirectory)
        connector.daemonBaseDir(file("daemon"))
        connector.embedded(false)
        connector.daemonMaxIdleTime(60, TimeUnit.SECONDS)

        // Reuse one connection (one daemon) so any corrupted/treeified shared state would persist across requests.
        def connection = connector.connect()
        try {
            FETCH_ITERATIONS.times { iteration ->
                // IP implies the configuration cache and caches each project's configuration independently.
                // Mutate the settings file (a build-tree-level input) each iteration to force a full CC miss, so
                // every fetch re-applies IdeaPlugin to all projects in parallel (rather than reusing cached
                // per-project configurations after the first iteration).
                settingsFile << "\n// iteration $iteration\n"
                def model = connection.model(IdeaProject)
                    .withArguments(*IP_PARALLEL)
                    .get()
                assert model.modules.size() >= PROJECT_COUNT
            }
        } finally {
            connection.close()
        }

        then:
        noExceptionThrown()
    }
}
