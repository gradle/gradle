/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.plugins

import org.gradle.api.internal.component.BuildableJavaComponent
import org.gradle.api.internal.component.ComponentRegistry
import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class JavaPluginIntegrationTest extends AbstractIntegrationSpec {
    def appliesBasePluginsAndAddsConventionObject() {
        given:
        buildFile << """
            apply plugin: 'java'
            
            task expect {
                doLast {
                    def component = project.services.get(${ComponentRegistry.canonicalName}).mainComponent
                    
                    assert component instanceof ${BuildableJavaComponent.canonicalName}
                    assert component.buildTasks as List == [ JavaBasePlugin.BUILD_TASK_NAME ]
                    assert component.runtimeClasspath != null
                    assert component.compileDependencies == project.configurations.compileClasspath
                }
            }
        """
        expect:
        succeeds "expect"
    }

    def "settings classesDir restores old behavior"() {
        buildFile << """
            apply plugin: 'java'
            
            def oldPath = file("build/classes/main")
            sourceSets.main.output.classesDir = oldPath
            assert sourceSets.main.java.outputDir == oldPath
            assert sourceSets.main.output.classesDir == oldPath
            assert sourceSets.main.output.classesDirs.contains(oldPath) 
        """
        file("src/main/java/Main.java") << """
            public class Main {}
        """
        when:
        executer.expectDeprecationWarning()
        succeeds("assemble")
        then:
        file("build/classes/java/main").assertDoesNotExist()
        file("build/classes/main/Main.class").assertExists()
        outputContains("Gradle now uses separate output directories for each JVM language, but this build assumes a single directory for all classes from a source set.")
    }

    def "emits deprecation message if something uses classesDir"() {
        buildFile << """
            apply plugin: 'java'
            
            def newPath = file("build/classes/java/main")
            assert sourceSets.main.output.classesDir == newPath
        """
        when:
        executer.expectDeprecationWarning()
        succeeds("help")
        then:
        outputContains("Gradle now uses separate output directories for each JVM language, but this build assumes a single directory for all classes from a source set.")
    }

    // TODO: This isn't done yet, we still realize many tasks
    // Eventually, this should only realize "help"
    def "does not realize all possible tasks"() {
        buildFile << """
            apply plugin: 'java'
            
            def configuredTasks = []
            tasks.configureEachLater {
                configuredTasks << it
            }
            
            gradle.buildFinished {
                assert configuredTasks.size() == 3
                def configuredTaskPaths = configuredTasks*.path
                // This should be the only task configured
                assert ":help" in configuredTaskPaths
                
                // This task needs to be able to register publications lazily
                assert ":jar" in configuredTaskPaths
                
                // This task is eagerly configured with configureEachLater
                assert ":test" in configuredTaskPaths
            }
        """
        expect:
        succeeds("help")
    }
}
