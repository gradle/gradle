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

@TargetVersions('0.9-rc-3+')
class PluginCrossVersionIntegrationTest extends CrossVersionIntegrationSpec {
    def "can use plugin compiled using previous Gradle version"() {
        given:
        file("producer/build.gradle") << """
apply plugin: 'groovy'
dependencies {
    compile localGroovy()
    compile gradleApi()
}
"""
        file("producer/src/main/groovy/SomePlugin.groovy") << """
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.*
import org.gradle.api.internal.ConventionTask

class SomePlugin implements Plugin<Project> {
    void apply(Project p) {
        p.tasks.add('do-stuff', CustomTask)
        p.tasks.add('customConventionTask', CustomConventionTask)
        p.tasks.add('customSourceTask', CustomSourceTask)
    }
}

class CustomTask extends DefaultTask {
    @TaskAction void go() { }
}

// ConventionTask leaks a lot of internal API so test for compatibility
class CustomConventionTask extends ConventionTask {}

// Same reason here, but less direct
class CustomSourceTask extends SourceTask {}

"""

        buildFile << """
buildscript {
    dependencies { classpath fileTree(dir: "producer/build/libs", include: '*.jar') }
}

apply plugin: SomePlugin
"""

        expect:
        version previous withTasks 'assemble' inDirectory(file("producer")) run()
        version current withTasks 'do-stuff' run()
    }
}
