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
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.tooling.BuildLauncher
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ModelBuilder
import org.gradle.tooling.ProgressEvent
import org.gradle.tooling.ProgressListener
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.eclipse.EclipseProject
/**
 * Tooling client provides progress listener for composite model request
 */
class ProgressListenerCompositeBuildCrossVersionSpec extends CompositeToolingApiSpecification {
    static final List<String> IGNORED_EVENTS = ['Validate distribution', '', 'Compiling script into cache', 'Build', 'Starting Gradle Daemon', 'Connecting to Gradle Daemon']
    AbstractCapturingProgressListener progressListenerForComposite
    AbstractCapturingProgressListener progressListenerForRegularBuild

    def "compare old listener events from a composite build and a regular build with single build"() {
        given:
        def builds = createBuilds(1)
        createListeners(CapturingProgressListener)

        when:
        requestModels(builds)
        then:
        assertListenerReceivedSameEventsInCompositeAndRegularConnections()
    }

    @TargetGradleVersion(">=2.5")
    def "compare new listener events from a composite build and a regular build with single build"() {
        given:
        def builds = createBuilds(1)
        createListeners(CapturingEventProgressListener)

        when:
        requestModels(builds)
        then:
        assertListenerReceivedSameEventsInCompositeAndRegularConnections()
    }

    def "compare old listener events executing tasks from a composite build and a regular build with single build"() {
        given:
        def builds = createBuilds(1)
        createListeners(CapturingProgressListener)

        when:
        executeFirstBuild(builds)
        then:
        assertListenerReceivedSameEventsInCompositeAndRegularConnections()
    }

    @TargetGradleVersion(">=2.5")
    def "compare new listener events executing tasks from a composite build and a regular build with single build"() {
        given:
        def builds = createBuilds(1)
        createListeners(CapturingEventProgressListener)

        when:
        executeFirstBuild(builds)
        then:
        assertListenerReceivedSameEventsInCompositeAndRegularConnections()
    }

    def "compare old listener events from a composite build and a regular build with 3 builds"() {
        given:
        def builds = createBuilds(3)
        createListeners(CapturingProgressListener)

        when:
        requestModels(builds)
        then:
        assertListenerReceivedSameEventsInCompositeAndRegularConnections()
    }

    @TargetGradleVersion(">=2.5")
    def "compare new listener events from a composite build and a regular build with 3 builds"() {
        given:
        def builds = createBuilds(3)
        createListeners(CapturingEventProgressListener)

        when:
        requestModels(builds)
        then:
        assertListenerReceivedSameEventsInCompositeAndRegularConnections()
    }

    def "compare old listener events from task execution from a composite build and a regular build with 3 builds"() {
        given:
        def builds = createBuilds(3)
        createListeners(CapturingProgressListener)

        when:
        executeFirstBuild(builds)
        then:
        assertListenerReceivedSameEventsInCompositeAndRegularConnections()
    }

    @TargetGradleVersion(">=2.5")
    def "compare new listener events from task execution from a composite build and a regular build with 3 builds"() {
        given:
        def builds = createBuilds(3)
        createListeners(CapturingEventProgressListener)

        when:
        executeFirstBuild(builds)
        then:
        assertListenerReceivedSameEventsInCompositeAndRegularConnections()
    }

    private void createListeners(Class progressListenerType) {
        progressListenerForComposite = DirectInstantiator.instantiate(progressListenerType)
        progressListenerForRegularBuild = DirectInstantiator.instantiate(progressListenerType)
    }

    private void assertListenerReceivedSameEventsInCompositeAndRegularConnections() {
        def regularBuildEvents = cleanEvents(progressListenerForRegularBuild.eventDescriptions)
        def compositeBuildEvents = cleanEvents(progressListenerForComposite.eventDescriptions)

        regularBuildEvents.each { eventDescription ->
            assert compositeBuildEvents.contains(eventDescription)
            compositeBuildEvents.remove(eventDescription)
        }
    }

    private static cleanEvents(List<String> eventDescriptions) {
        def copy = []
        String previous = null
        for (String event : eventDescriptions) {
            if (IGNORED_EVENTS.contains(event)) {
                continue;
            }
            if (event != previous) {
                copy.add event
                previous = event
            }
        }

        assert !copy.empty
        return copy
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
        withCompositeConnection(builds) { connection ->
            BuildLauncher buildLauncher = connection.newBuild()
            buildLauncher.forTasks(builds[0], "jar")
            buildLauncher.addProgressListener(progressListenerForComposite)
            buildLauncher.run()
        }

        GradleConnector connector = toolingApi.connector()
        connector.forProjectDirectory(builds[0].absoluteFile)
        toolingApi.withConnection(connector) { ProjectConnection connection ->
            BuildLauncher buildLauncher = connection.newBuild()
            buildLauncher.forTasks("jar")
            buildLauncher.addProgressListener(progressListenerForRegularBuild)
            buildLauncher.run()
        }
    }

    private def getModels(ModelBuilder modelBuilder, progressListener) {
        modelBuilder.addProgressListener(progressListener)
        modelBuilder.get()
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
