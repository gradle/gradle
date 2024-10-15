/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.project

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.services.ServiceReference
import org.gradle.api.tasks.TaskAction
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationListener
import org.gradle.internal.operations.OperationFinishEvent
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.operations.OperationProgressEvent
import org.gradle.internal.operations.OperationStartEvent
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

@Requires(IntegTestPreconditions.NotIsolatedProjects) // IP mode introduces different equality model because of projects wrapping
class ProjectEqualityContractIntegrationTest extends AbstractIntegrationSpec {

    def 'Symmetrical equality between raw and wrapped projects'() {
        given:
        buildFile("buildSrc/build.gradle", """
            plugins {
                id 'groovy'
            }
        """)
        groovyFile("buildSrc/src/main/groovy/EqualsContractCheckerService.groovy", """
            import ${BuildService.name}
            import ${BuildServiceParameters.name}
            import ${Project.name}
            import ${BuildOperationListener.name}

            // We need this build service instance to survive after configuration. The only way to have it is to implement `BuildOperationListener`
            public abstract class EqualsContractCheckerService implements BuildService<BuildServiceParameters.None>, BuildOperationListener {
                private Map<String, Project> projectsToCheck = [:]

                public def addProjectToCheck(String description, Project project) {
                    projectsToCheck[description] = project
                }

                public Map<String, Project> getProjectsToCheck() {
                    return projectsToCheck
                }

                void started($BuildOperationDescriptor.name buildOperation, $OperationStartEvent.name startEvent){}

                void progress($OperationIdentifier.name operationIdentifier, $OperationProgressEvent.name progressEvent){}

                void finished($BuildOperationDescriptor.name buildOperation, $OperationFinishEvent.name finishEvent){}
            }
        """)
        groovyFile("buildSrc/src/main/groovy/EqualsContractCheckerTask.groovy", """
            import ${ServiceReference.name}
            import ${DefaultTask.name}
            import ${Property.name}
            import ${TaskAction.name}

            public abstract class EqualsContractCheckerTask extends DefaultTask {
                @ServiceReference("equalsContractChecker")
                abstract Property<EqualsContractCheckerService> getService()

                @TaskAction
                def check() {
                    def projectsToCheck = getService().get().getProjectsToCheck()
                    [projectsToCheck.entrySet(), projectsToCheck.entrySet()].combinations()
                        .findAll { a, b -> a.key != b.key }
                        .forEach { a, b -> println("\${a.key} equals to \${b.key}: \${a.value.equals(b.value)}") }
                }
            }
        """)

        settingsFile """
            include(":a")
            include(":b")
        """

        buildFile """
            def equalsContractCheckerProvider = gradle.getSharedServices().registerIfAbsent("equalsContractChecker", EqualsContractCheckerService.class)
            def equalsContractChecker = equalsContractCheckerProvider.get()
            equalsContractChecker.addProjectToCheck(":a wrapped by :root#subprojects", subprojects.toList().get(0))
            equalsContractChecker.addProjectToCheck(":a wrapped by :root#allprojects", allprojects.toList().get(1))
            equalsContractChecker.addProjectToCheck(":a wrapped by :root#project", project(":a"))

            def registry = services.get(BuildEventsListenerRegistry)
            registry.onOperationCompletion(equalsContractCheckerProvider)
        """

        buildFile("a/build.gradle", """
            def equalsContractChecker = gradle.getSharedServices().registerIfAbsent("equalsContractChecker", EqualsContractCheckerService.class).get()
            equalsContractChecker.addProjectToCheck("raw :a", project)

            tasks.register("checkEqualsContract", EqualsContractCheckerTask.class)
        """)

        buildFile("b/build.gradle", """
            def equalsContractChecker = gradle.getSharedServices().registerIfAbsent("equalsContractChecker", EqualsContractCheckerService.class).get()
            equalsContractChecker.addProjectToCheck(":a wrapped by :b#project", project(":a"))
        """)

        when:
        run "checkEqualsContract"

        then:
        outputContains(":a wrapped by :root#allprojects equals to :a wrapped by :root#subprojects: true")
        outputContains(":a wrapped by :root#allprojects equals to :a wrapped by :root#project: true")
        outputContains(":a wrapped by :root#allprojects equals to :a wrapped by :b#project: true")
        outputContains(":a wrapped by :root#allprojects equals to raw :a: true")

        outputContains(":a wrapped by :b#project equals to :a wrapped by :root#allprojects: true")
        outputContains(":a wrapped by :b#project equals to :a wrapped by :root#subprojects: true")
        outputContains(":a wrapped by :b#project equals to :a wrapped by :root#project: true")
        outputContains(":a wrapped by :b#project equals to raw :a: true")

        outputContains(":a wrapped by :root#project equals to :a wrapped by :root#allprojects: true")
        outputContains(":a wrapped by :root#project equals to :a wrapped by :root#subprojects: true")
        outputContains(":a wrapped by :root#project equals to :a wrapped by :b#project: true")
        outputContains(":a wrapped by :root#project equals to raw :a: true")

        outputContains(":a wrapped by :root#subprojects equals to :a wrapped by :root#allprojects: true")
        outputContains(":a wrapped by :root#subprojects equals to :a wrapped by :root#project: true")
        outputContains(":a wrapped by :root#subprojects equals to :a wrapped by :b#project: true")
        outputContains(":a wrapped by :root#subprojects equals to raw :a: true")

        outputContains("raw :a equals to :a wrapped by :root#subprojects: true")
        outputContains("raw :a equals to :a wrapped by :root#allprojects: true")
        outputContains("raw :a equals to :a wrapped by :root#project: true")
        outputContains("raw :a equals to :a wrapped by :b#project: true")
    }

    def 'Raw and wrapped projects are interchangeable when using as keys in hashCode-based data structures'() {
        given:
        buildFile("buildSrc/build.gradle", """
            plugins {
                id 'groovy'
            }
        """)
        groovyFile("buildSrc/src/main/groovy/MapCollectorService.groovy", """
            import ${BuildService.name}
            import ${BuildServiceParameters.name}
            import ${Project.name}
            import ${BuildOperationListener.name}

            // We need this build service instance to survive after configuration. The only way to have it is to implement `BuildOperationListener`
            public abstract class MapCollectorService implements BuildService<BuildServiceParameters.None>, BuildOperationListener {
                private Map<Project, String> collectedProjects = [:]

                public def collectProject(Project project, String description) {
                    def last = collectedProjects.put(project, description)
                    println("Last collected value for \$project is \$last")
                }

                public Map<String, Project> getCollectedProjects() {
                    return collectedProjects
                }

                void started($BuildOperationDescriptor.name buildOperation, $OperationStartEvent.name startEvent){}

                void progress($OperationIdentifier.name operationIdentifier, $OperationProgressEvent.name progressEvent){}

                void finished($BuildOperationDescriptor.name buildOperation, $OperationFinishEvent.name finishEvent){}
            }
        """)
        groovyFile("buildSrc/src/main/groovy/MapCollectorTask.groovy", """
            import ${ServiceReference.name}
            import ${DefaultTask.name}
            import ${Property.name}
            import ${TaskAction.name}

            public abstract class MapCollectorTask extends DefaultTask {
                @ServiceReference("mapCollector")
                abstract Property<MapCollectorService> getService()

                @TaskAction
                def collect() {
                    def collectedProjects = getService().get().getCollectedProjects()
                    println("Collected projects: \$collectedProjects")
                }
            }
        """)

        settingsFile """
            include(":a")
        """

        buildFile """
            def mapCollectorServiceProvider = gradle.getSharedServices().registerIfAbsent("mapCollector", MapCollectorService.class)
            def mapCollectorService = mapCollectorServiceProvider.get()
            mapCollectorService.collectProject(subprojects.toList().get(0), ":a wrapped by :root#subprojects")
            mapCollectorService.collectProject(allprojects.toList().get(1), ":a wrapped by :root#allprojects")
            mapCollectorService.collectProject(project(":a"), ":a wrapped by :root#project")

            def registry = services.get(BuildEventsListenerRegistry)
            registry.onOperationCompletion(mapCollectorServiceProvider)
        """

        buildFile("a/build.gradle", """
            def mapCollectorService = gradle.getSharedServices().registerIfAbsent("mapCollector", MapCollectorService.class).get()
            mapCollectorService.collectProject(project, "raw :a")

            tasks.register("collectProjects", MapCollectorTask.class)
        """)

        when:
        run "collectProjects"

        then:
        outputContains("Last collected value for project ':a' is null")
        outputContains("Last collected value for project ':a' is :a wrapped by :root#subprojects")
        outputContains("Last collected value for project ':a' is :a wrapped by :root#allprojects")
        outputContains("Last collected value for project ':a' is :a wrapped by :root#project")
        outputContains("Collected projects: [project ':a':raw :a]")
    }
}
