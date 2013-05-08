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

import org.gradle.api.Project
import org.gradle.api.tasks.JavaSourceSet
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.language.jvm.ClassDirectoryBinary
import org.gradle.language.jvm.plugins.JvmLanguagePlugin
import org.gradle.util.HelperUtil

import spock.lang.Specification

class JavaLanguagePluginTest extends Specification {
    Project project = HelperUtil.createRootProject()

    def setup() {
        project.plugins.apply(JavaLanguagePlugin)
    }

    def "applies jvm-lang plugin"() {
        expect:
        project.plugins.hasPlugin(JvmLanguagePlugin)
    }

    def "adds a JavaCompile task for every JavaSourceSet added to a ClassDirectoryBinary"() {
        when:
        project.sources.create("model").create("java", JavaSourceSet)
        project.binaries.create("integTest", ClassDirectoryBinary)
        project.binaries.integTest.source << project.sources.model.java

        then:
        def task = project.tasks.findByName("compileIntegTestJava")
        task instanceof JavaCompile
        task.description == "Compiles source set 'model:java'."
    }
}
