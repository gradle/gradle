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
import org.gradle.api.jvm.ClassDirectoryBinarySpec
import org.gradle.language.jvm.ResourceSet
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.util.TestUtil
import spock.lang.Specification

class JvmLanguagePluginTest extends Specification {
    def project = TestUtil.createRootProject()
    def jvmLanguagePlugin = project.plugins.apply(JvmLanguagePlugin)

    def "registers the 'ResourceSet' type for each functional source set added to the 'sources' container"() {
        when:
        project.sources.create("custom")
        project.sources.custom.create("resources", ResourceSet)

        then:
        project.sources.custom.resources instanceof ResourceSet
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
        def binary = project.binaries.create("prod", ClassDirectoryBinarySpec)

        then:
        binary.classesDir == new File("$project.buildDir/classes/prod")
        def task = project.tasks.findByName("prodClasses")
        task != null
        task.description == "Assembles classes 'prod'."
    }

    def "adds a 'processResources' task for every ResourceSet added to a ClassDirectoryBinary"() {
        ClassDirectoryBinarySpec binary = project.binaries.create("prod", ClassDirectoryBinarySpec)
        ResourceSet resources = project.sources.create("main").create("resources", ResourceSet)

        when:
        binary.source.add(resources)

        then:
        project.tasks.size() == old(project.tasks.size()) + 1
        def task = project.tasks.findByName("processProdResources")
        task instanceof ProcessResources
        task.description == "Processes resources 'main:resources'."
    }

    def "adds tasks based on short name when ClassDirectoryBinary has name ending in Classes"() {
        when:
        ClassDirectoryBinarySpec binary = project.binaries.create("fooClasses", ClassDirectoryBinarySpec)
        ResourceSet resources = project.sources.create("main").create("resources", ResourceSet)
        binary.source.add(resources)

        then:
        binary.classesDir == new File("$project.buildDir/classes/foo")
        def task = project.tasks.findByName("fooClasses")
        task != null
        task.description == "Assembles classes 'foo'."

        and:
        def resourcesTask = project.tasks.findByName("processFooResources")
        resourcesTask instanceof ProcessResources
        resourcesTask.description == "Processes resources 'main:resources'."
    }

    def "binary tasks are available via binary.tasks"() {
        ClassDirectoryBinarySpec binary = project.binaries.create("prod", ClassDirectoryBinarySpec)
        ResourceSet resources = project.sources.create("main").create("resources", ResourceSet)

        when:
        binary.source.add(resources)

        then:
        def classesTask = project.tasks.findByName("prodClasses")
        def resourcesTask = project.tasks.findByName("processProdResources")

        binary.tasks.build == classesTask
        binary.tasks as Set == [resourcesTask] as Set
    }
}
