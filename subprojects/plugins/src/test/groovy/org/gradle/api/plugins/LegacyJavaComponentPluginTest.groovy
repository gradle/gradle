/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.api.internal.jvm.DefaultClassDirectoryBinarySpec
import org.gradle.api.internal.project.DefaultProject
import org.gradle.jvm.ClassDirectoryBinarySpec
import org.gradle.util.TestUtil
import spock.lang.Specification

class LegacyJavaComponentPluginTest extends Specification {
    DefaultProject project = TestUtil.createRootProject()

    def setup() {
        project.pluginManager.apply(LegacyJavaComponentPlugin)
    }

    def "applies jvm-lang plugin"() {
        expect:
        project.plugins.hasPlugin(LegacyJavaComponentPlugin)
    }

    def "registers the ClassDirectoryBinary type with the binaries container"() {
        def binaries = project.extensions.findByName("binaries")
        def binary = binaries.create("test", ClassDirectoryBinarySpec)

        expect:
        binary != null
        binary instanceof DefaultClassDirectoryBinarySpec
    }

    def "adds a 'classes' task for every ClassDirectoryBinary added to the container"() {
        when:
        ClassDirectoryBinarySpec binary = project.binaries.create("prod", ClassDirectoryBinarySpec)

        then:
        def task = project.tasks.findByName("prodClasses")
        task != null
        task.description == "Assembles classes 'prod'."

        and:
        binary.buildTask == task
    }
}
