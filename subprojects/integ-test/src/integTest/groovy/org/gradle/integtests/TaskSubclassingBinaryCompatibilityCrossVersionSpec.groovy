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
package org.gradle.integtests

import org.gradle.integtests.fixtures.CrossVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetVersions
import org.gradle.util.GradleVersion

/**
 * Tests that task classes compiled against earlier versions of Gradle are still compatible.
 */
@TargetVersions('0.9-rc-3+')
class TaskSubclassingBinaryCompatibilityCrossVersionSpec extends CrossVersionIntegrationSpec {
    def "can use task subclass compiled using previous Gradle version"() {
        given:
        def taskClasses = [
                "DefaultTask", "SourceTask", "ConventionTask",
                "Copy", "Sync", "Zip", "Jar", "Tar", "War", "Ear"
        ]

        Map<String, String> subclasses = taskClasses.collectEntries { ["custom" + it, it] }

        file("producer/build.gradle") << """
            apply plugin: 'groovy'
            dependencies {
                ${previous.version < GradleVersion.version("1.4-rc-1") ? "groovy" : "compile" } localGroovy()
                compile gradleApi()
            }
        """

        file("producer/src/main/groovy/SomePlugin.groovy") << """
            import org.gradle.api.Plugin
            import org.gradle.api.Project
            import org.gradle.api.DefaultTask
            import org.gradle.api.tasks.*
            import org.gradle.api.tasks.bundling.*
            import org.gradle.plugins.ear.Ear
            import org.gradle.api.internal.ConventionTask

            class SomePlugin implements Plugin<Project> {
                void apply(Project p) { """ <<
                subclasses.collect { "p.tasks.add('${it.key}', ${it.key.capitalize()})" }.join("\n") << """
                }
            }
            """ <<

                subclasses.collect {
                    "class ${it.key.capitalize()} extends ${it.value} {}"
                }.join("\n")

        buildFile << """
buildscript {
    dependencies { classpath fileTree(dir: "producer/build/libs", include: '*.jar') }
}

apply plugin: SomePlugin
"""

        expect:
        version previous withTasks 'assemble' inDirectory(file("producer")) run()
        version current withTasks 'tasks' run()
    }
}
