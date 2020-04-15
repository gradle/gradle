/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.play.plugins

import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.file.ProjectLayout
import org.gradle.api.internal.project.ProjectIdentifier
import org.gradle.api.tasks.testing.Test
import org.gradle.language.scala.tasks.PlatformScalaCompile
import org.gradle.model.ModelMap
import org.gradle.platform.base.BinaryTasksCollection
import org.gradle.play.internal.PlayApplicationBinarySpecInternal
import spock.lang.Specification

class PlayTestPluginTest extends Specification {

    ModelMap<Task> taskModelMap = Mock(ModelMap)
    def binaryContainer = Mock(ModelMap)
    def projectIdentifier = Mock(ProjectIdentifier)
    def binary = Mock(PlayApplicationBinarySpecInternal)

    def configuration = Stub(Configuration)
    def configurations = Mock(ConfigurationContainer)
    def dependencyHandler = Mock(DependencyHandler)

    File buildDir = new File("tmp")
    File jarFile = new File("file.jar")

    PlayTestPlugin plugin = new PlayTestPlugin()

    def setup(){
        _ * binaryContainer.iterator() >> [binary].iterator()
        _ * binary.projectScopedName >> "someBinary"
        _ * binary.getTasks() >> Stub(BinaryTasksCollection) {
            taskName(_, _) >> { String verb, String object -> "${verb}SomeBinary${object.capitalize()}"}
            taskName(_) >> { String verb -> "${verb}SomeBinary"}
        }
        1 * binary.jarFile >> jarFile

        _ * configurations.create(_) >> configuration
        _ * configurations.maybeCreate(_) >> configuration
        _ * configurations.getByName(_) >> configuration
        0 * _
    }

    def "adds test related tasks per binary"() {
        given:
        def projectLayout = Stub(ProjectLayout)

        when:
        plugin.createTestTasks(taskModelMap, binaryContainer, new PlayPluginConfigurations(configurations, dependencyHandler), projectLayout, projectIdentifier, buildDir)

        then:
        1 * taskModelMap.create("compileSomeBinaryTests", PlatformScalaCompile, _)
        1 * taskModelMap.create("testSomeBinary", Test, _)
        0 * taskModelMap.create(_)
        0 * taskModelMap.create(_, _, _)
        1 * taskModelMap.get('testSomeBinary')
    }
}
