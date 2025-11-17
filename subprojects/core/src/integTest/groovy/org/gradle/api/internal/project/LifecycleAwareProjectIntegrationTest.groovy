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

import org.gradle.api.Project
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.BuildLogicGcTrigger
import org.gradle.integtests.fixtures.ProjectDirectoryCreator
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationListener
import org.gradle.internal.operations.OperationFinishEvent
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.operations.OperationProgressEvent
import org.gradle.internal.operations.OperationStartEvent
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.IntegTestPreconditions

@Requires(IntegTestPreconditions.NotIsolatedProjects)
class LifecycleAwareProjectIntegrationTest extends AbstractIntegrationSpec implements ProjectDirectoryCreator, BuildLogicGcTrigger {

    def 'Different equal instances of LifecycleAwareProject bear the same state'() {
        given:
        settingsFile """
            rootProject.name = 'root'
        """
        includeProjects("a")
        buildFile """
            allprojects {
                ext.foo = "bar"
            }
            allprojects {
                println("\$name foo=\$foo")
            }
        """

        when:
        run "help", "-q"

        then:
        outputContains "root foo=bar\na foo=bar"
    }

    def 'LifecycleAwareProject delegates hasProperty correctly'() {
        given:
        settingsFile """
            rootProject.name = 'root'
            include(":a")
        """
        file("a/build.gradle") << ""
        buildFile """
            project(':a') {
                println("a contains foo: \${it.hasProperty('foo')}")
            }
        """

        when:
        run "help", "-Pfoo=bar"

        then:
        outputContains "a contains foo: true"
    }

    def 'LifecycleAwareProject delegates setProperty correctly'() {
        given:
        settingsFile """
            rootProject.name = 'root'
            include(":a")
        """
        file("a/build.gradle") << ""
        buildFile """
            project(':a') {
                foo='bar1'
            }
            project(':a') {
                println("a foo=\$foo")
            }
        """

        when:
        run "help", "-Pfoo=bar"

        then:
        outputContains "a foo=bar1"
    }

    def 'LifecycleAwareProject instances are ephemeral'() {
        given:
        file("buildSrc/build.gradle") << """
            plugins {
                id 'groovy'
            }
        """
        file("buildSrc/src/main/groovy/GlobalWeakStorage.groovy") << """
            import java.util.WeakHashMap
            import ${BuildService.name}
            import ${BuildServiceParameters.name}
            import ${Project.name}
            import ${BuildOperationListener.name}

            public abstract class GlobalWeakStorage implements BuildService<BuildServiceParameters.None>, BuildOperationListener {
                private WeakHashMap map = new WeakHashMap<${Project.name}, String>()
                void add(${Project.name} project) {
                    map.put(project, project.toString())
                }
                Map<${Project.name}, String> getAll() {
                    return map
                }
                void started($BuildOperationDescriptor.name buildOperation, $OperationStartEvent.name startEvent){}
                void progress($OperationIdentifier.name operationIdentifier, $OperationProgressEvent.name progressEvent){}
                void finished($BuildOperationDescriptor.name buildOperation, $OperationFinishEvent.name finishEvent){}
            }
        """
        settingsFile """
            rootProject.name = 'root'
            include(":a")
        """
        buildFile("a/build.gradle", "")
        buildFile """
            ${withGcTriggerDeclaration()}
            def globalWeakStorage = gradle.sharedServices.registerIfAbsent("globalWeakStorage", GlobalWeakStorage.class).get()
            globalWeakStorage.add(project(":a"))
            requireGc()
            globalWeakStorage.getAll().each { project, name ->
                println("\${project.class} to \$name")
            }
        """

        when:
        run "help"

        then:
        outputDoesNotContain("class org.gradle.api.internal.project.LifecycleAwareProject_Decorated to project ':a'")
    }
}
