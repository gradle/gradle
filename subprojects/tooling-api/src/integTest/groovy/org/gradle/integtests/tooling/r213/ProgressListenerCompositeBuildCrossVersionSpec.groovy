/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.integtests.tooling.r213

import org.gradle.integtests.tooling.fixture.CompositeToolingApiSpecification
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.tooling.*
import org.gradle.tooling.model.eclipse.EclipseProject
import org.gradle.util.GradleVersion
import org.junit.Assume

/**
 * Tooling client provides progress listener for composite model request
 */
class ProgressListenerCompositeBuildCrossVersionSpec extends CompositeToolingApiSpecification {
    static final List<String> IGNORED_EVENTS = ['Validate distribution', '', 'Compiling script into cache', 'Build']
    AbstractCapturingProgressListener progressListenerForComposite
    AbstractCapturingProgressListener progressListenerForRegularBuild

    private boolean progressListenerSupported(Class progressListenerType) {
        if (progressListenerType == CapturingEventProgressListener) {
            return targetDist.version >= GradleVersion.version("2.5")
        }
        return true
    }

    def "compare events from a composite build and a regular build with single build"() {
        given:
        Assume.assumeTrue(progressListenerSupported(progressListenerType))

        def builds = createBuilds(1)
        progressListenerForComposite = DirectInstantiator.instantiate(progressListenerType)
        progressListenerForRegularBuild = DirectInstantiator.instantiate(progressListenerType)
        println "Progress listener type ${progressListenerType.canonicalName}"

        when:
        requestModels(builds)
        then:
        assertListenerReceivedSameEventsInCompositeAndRegularConnections()

        where:
        progressListenerType << [ CapturingProgressListener, CapturingEventProgressListener ]
    }

    def "compare events executing tasks from a composite build and a regular build with single build"() {
        given:
        Assume.assumeTrue(progressListenerSupported(progressListenerType))
        skipForDaemonCoordinator()

        def builds = createBuilds(1)
        progressListenerForComposite = DirectInstantiator.instantiate(progressListenerType)
        progressListenerForRegularBuild = DirectInstantiator.instantiate(progressListenerType)
        println "Progress listener type ${progressListenerType.canonicalName}"

        when:
        executeFirstBuild(builds)
        then:
        assertListenerReceivedSameEventsInCompositeAndRegularConnections()

        where:
        progressListenerType << [ CapturingProgressListener, CapturingEventProgressListener ]
    }

    def "compare events from a composite build and a regular build with 3 builds"() {
        given:
        Assume.assumeTrue(progressListenerSupported(progressListenerType))

        def builds = createBuilds(3)
        progressListenerForComposite = DirectInstantiator.instantiate(progressListenerType)
        progressListenerForRegularBuild = DirectInstantiator.instantiate(progressListenerType)
        println "Progress listener type ${progressListenerType.canonicalName}"

        when:
        requestModels(builds)
        then:
        assertListenerReceivedSameEventsInCompositeAndRegularConnections()

        where:
        progressListenerType << [ CapturingProgressListener, CapturingEventProgressListener ]
    }

    def "compare events from task execution from a composite build and a regular build with 3 builds"() {
        given:
        Assume.assumeTrue(progressListenerSupported(progressListenerType))
        skipForDaemonCoordinator()

        def builds = createBuilds(3)
        progressListenerForComposite = DirectInstantiator.instantiate(progressListenerType)
        progressListenerForRegularBuild = DirectInstantiator.instantiate(progressListenerType)
        println "Progress listener type ${progressListenerType.canonicalName}"

        when:
        executeFirstBuild(builds)
        then:
        assertListenerReceivedSameEventsInCompositeAndRegularConnections()

        where:
        progressListenerType << [ CapturingProgressListener, CapturingEventProgressListener ]
    }

    private void assertListenerReceivedSameEventsInCompositeAndRegularConnections() {
        assert !progressListenerForRegularBuild.eventDescriptions.isEmpty()
        assert !progressListenerForComposite.eventDescriptions.isEmpty()
        progressListenerForRegularBuild.eventDescriptions.each { eventDescription ->
            if (!(eventDescription in IGNORED_EVENTS)) {
                assert progressListenerForComposite.eventDescriptions.contains(eventDescription)
                progressListenerForComposite.eventDescriptions.remove(eventDescription)
            }
        }
    }

    private List<File> createBuilds(int numberOfBuilds) {
        def builds = (1..numberOfBuilds).collect {
            populate("build-$it") {
                buildFile << "apply plugin: 'java'"
            }
        }
        return builds
    }

    private void requestModels(List<File> builds) {
        withCompositeConnection(builds) { connection ->
            getModels(connection.models(EclipseProject), progressListenerForComposite)
        }

        builds.each { buildDir ->
            GradleConnector connector = toolingApi.connector()
            connector.forProjectDirectory(buildDir.absoluteFile)
            toolingApi.withConnection(connector) { ProjectConnection connection ->
                getModels(connection.model(EclipseProject), progressListenerForRegularBuild)
            }
        }
    }

    private void executeFirstBuild(List<File> builds) {
        def buildId = createGradleBuildParticipant(builds[0]).toBuildIdentity()
        withCompositeConnection(builds) { connection ->
            runBuild(connection.newBuild(buildId), progressListenerForComposite)
        }

        GradleConnector connector = toolingApi.connector()
        connector.forProjectDirectory(builds[0].absoluteFile)
        toolingApi.withConnection(connector) { ProjectConnection connection ->
            runBuild(connection.newBuild(), progressListenerForRegularBuild)
        }
    }

    private def getModels(ModelBuilder modelBuilder, progressListener) {
        modelBuilder.addProgressListener(progressListener)
        modelBuilder.get()
    }

    private void runBuild(BuildLauncher buildLauncher, progressListener) {
        buildLauncher.forTasks("jar")
        buildLauncher.addProgressListener(progressListener)
        buildLauncher.run()
    }

    static abstract class AbstractCapturingProgressListener {
        def eventDescriptions = []
    }

    static class CapturingProgressListener extends AbstractCapturingProgressListener implements ProgressListener {
        @Override
        void statusChanged(ProgressEvent event) {
            eventDescriptions.add(event.description)
        }
    }

    static class CapturingEventProgressListener extends AbstractCapturingProgressListener implements org.gradle.tooling.events.ProgressListener {
        @Override
        void statusChanged(org.gradle.tooling.events.ProgressEvent event) {
            eventDescriptions.add(event.descriptor.name)
        }
    }
}
