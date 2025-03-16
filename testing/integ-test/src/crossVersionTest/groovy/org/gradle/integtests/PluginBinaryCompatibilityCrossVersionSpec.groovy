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
package org.gradle.integtests

import org.gradle.integtests.fixtures.CrossVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetVersions
import org.gradle.util.GradleVersion

/**
 * Tests that task classes compiled against earlier versions of Gradle are still compatible.
 */
@TargetVersions("4.10+") // JavaPluginExtension did not exist before 4.10
class PluginBinaryCompatibilityCrossVersionSpec extends CrossVersionIntegrationSpec {
    def "plugin implemented in Groovy can use types converted from Groovy to Java"() {
        given:
        def apiDepConf = "implementation"
        if (previous.version < GradleVersion.version("6.0")) {
            apiDepConf = "compile"
        }

        file("producer/build.gradle") << """
            apply plugin: 'groovy'
            dependencies {
                ${apiDepConf} localGroovy()
                ${apiDepConf} gradleApi()
            }
        """

        file("producer/src/main/groovy/SomePlugin.groovy") << """
            import org.gradle.api.Plugin
            import org.gradle.api.Project
            import org.gradle.api.plugins.JavaPluginExtension
            import org.gradle.plugins.ide.idea.model.IdeaModule

            class SomePlugin implements Plugin<Project> {
                void apply(Project p) {
                    p.apply plugin: 'java'
                    p.apply plugin: 'idea'

                    // Verify can use the types with and without various type declarations

                    JavaPluginExtension c = p.extensions.java
                    c.sourceCompatibility = 1.8
                    println c.sourceCompatibility
                    c.manifest { }

                    GroovyObject o = p.extensions.java
                    o.sourceCompatibility = 1.7
                    println o.sourceCompatibility
                    o.manifest { }

                    def d = p.extensions.java
                    d.sourceCompatibility = 1.8
                    println d.sourceCompatibility
                    d.manifest { }

                    IdeaModule m = p.idea.module
                    m.name = '123'
                    println m.name
                }
            }
            """

        buildFile << """
buildscript {
    dependencies { classpath fileTree(dir: "producer/build/libs", include: '*.jar') }
}

apply plugin: SomePlugin
"""

        expect:
        version previous withTasks 'assemble' inDirectory(file("producer")) run()
        version current withTasks 'tasks' requireDaemon() requireIsolatedDaemons() run()
    }
}
