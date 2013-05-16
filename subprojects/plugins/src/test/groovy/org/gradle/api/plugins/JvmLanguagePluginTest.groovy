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
import org.gradle.language.jvm.ClassDirectoryBinary
import org.gradle.language.jvm.ResourceSet
import org.gradle.language.jvm.internal.DefaultClassDirectoryBinary
import org.gradle.language.jvm.plugins.JvmLanguagePlugin
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.util.HelperUtil
import spock.lang.Specification

class JvmLanguagePluginTest extends Specification {
    def project = HelperUtil.createRootProject()
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
        def binary = binaries.create("test", ClassDirectoryBinary)

        expect:
        binary != null
        binary instanceof DefaultClassDirectoryBinary
    }

    def "adds a 'classes' task for every ClassDirectoryBinary added to the container"() {
        when:
        def binary = project.binaries.create("prod", ClassDirectoryBinary)

        then:
        binary.classesDir == new File("$project.buildDir/classes/prod")
        def task = project.tasks.findByName("prodClasses")
        task != null
        task.description == "Assembles binary 'prod'."
    }

    def "adds a 'processResources' task for every ResourceSet added to a ClassDirectoryBinary"() {
        ClassDirectoryBinary binary = project.binaries.create("prod", ClassDirectoryBinary)
        ResourceSet resources = project.sources.create("main").create("resources", ResourceSet)

        when:
        binary.source.add(resources)

        then:
        project.tasks.size() == old(project.tasks.size()) + 1
        def task = project.tasks.findByName("processProdResources")
        task instanceof ProcessResources
        task.description == "Processes source set 'main:resources'."
    }

    def "adds tasks based on short name when ClassDirectoryBinary has name ending in Classes"() {
        when:
        ClassDirectoryBinary binary = project.binaries.create("fooClasses", ClassDirectoryBinary)
        ResourceSet resources = project.sources.create("main").create("resources", ResourceSet)
        binary.source.add(resources)

        then:
        binary.classesDir == new File("$project.buildDir/classes/foo")
        def task = project.tasks.findByName("fooClasses")
        task != null
        task.description == "Assembles binary 'fooClasses'."

        and:
        def resourcesTask = project.tasks.findByName("processFooResources")
        resourcesTask instanceof ProcessResources
        resourcesTask.description == "Processes source set 'main:resources'."
    }
}
