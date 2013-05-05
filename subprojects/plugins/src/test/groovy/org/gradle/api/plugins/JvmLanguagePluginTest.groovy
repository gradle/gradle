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

import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.language.jvm.ClassDirectoryBinary
import org.gradle.language.jvm.JvmBinaryContainer
import org.gradle.language.jvm.ResourceSet
import org.gradle.language.jvm.plugins.JvmLanguagePlugin
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

    def "adds a 'binaries.jvm' container to the project"() {
        def binaries = project.extensions.findByName("binaries")

        expect:
        binaries != null
        binaries.jvm instanceof JvmBinaryContainer
    }

    def "adds a 'classes' task for every ClassDirectoryBinary added to the container"() {
        when:
        def binary = project.binaries.jvm.create("prod", ClassDirectoryBinary)

        then:
        binary.classesDir == new File("$project.buildDir/classes/prod")
        def task = project.tasks.findByName("prodClasses")
        task != null
        task.description == "Assembles binary 'prod'."
    }

    def "adds a 'processResources' task for every ResourceSet added to a ClassDirectoryBinary"() {
        ClassDirectoryBinary binary = project.binaries.jvm.create("prod", ClassDirectoryBinary)
        ResourceSet resources = project.sources.create("main").create("resources", ResourceSet)

        when:
        binary.source.add(resources)

        then:
        project.tasks.size() == old(project.tasks.size()) + 1
        def task = project.tasks.findByName("processProdResources")
        task instanceof ProcessResources
        task.description == "Processes source set 'main:resources'."
    }
}
