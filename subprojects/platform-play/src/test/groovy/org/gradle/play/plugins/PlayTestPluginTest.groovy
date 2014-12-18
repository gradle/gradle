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
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.project.ProjectIdentifier
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.testing.Test
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.language.scala.tasks.PlatformScalaCompile
import org.gradle.model.collection.CollectionBuilder
import org.gradle.platform.base.BinaryContainer
import org.gradle.platform.base.BinaryTasksCollection
import org.gradle.play.PlayApplicationBinarySpec
import org.gradle.play.internal.toolchain.PlayToolChainInternal
import org.gradle.play.internal.toolchain.PlayToolProvider
import org.gradle.play.platform.PlayPlatform
import spock.lang.Specification

class PlayTestPluginTest extends Specification {

    CollectionBuilder<Task> taskCollectionBuilder = Mock(CollectionBuilder)
    def binaryContainer = Mock(BinaryContainer)
    def projectIdentifier = Mock(ProjectIdentifier)
    def binary = Mock(PlayApplicationBinarySpec)
    def playPlatform = Mock(PlayPlatform)
    def playToolChain = Mock(PlayToolChainInternal)
    def playToolProvider = Mock(PlayToolProvider)

    File buildDir = new File("tmp")

    PlayTestPlugin plugin = new PlayTestPlugin()

    def setup(){
        _ * binaryContainer.withType(PlayApplicationBinarySpec.class) >> binaryContainer
        _ * binaryContainer.iterator() >> [binary].iterator()
        _ * binary.name >> "someBinary"
    }

    def "adds test related tasks per binary"() {
        given:
        def fileResolver = Mock(FileResolver)
        def resolvedFiles = Mock(FileCollection);
        1 * fileResolver.resolveFiles(_) >> resolvedFiles

        1 * binary.getTargetPlatform() >> playPlatform
        1 * playToolChain.select(playPlatform) >> playToolProvider

        when:
        plugin.createTestTasks(taskCollectionBuilder, binaryContainer, playToolChain, fileResolver, projectIdentifier, buildDir)

        then:
        1 * taskCollectionBuilder.create("compileSomeBinaryTests", PlatformScalaCompile, _)
        1 * taskCollectionBuilder.create("testSomeBinary", Test, _)
        0 * taskCollectionBuilder.create(_)
        0 * taskCollectionBuilder.create(_, _, _)
    }

    def "adds play test task dependency to check task"() {
        given:
        def taskContainer = Mock(TaskContainer)
        def check = Mock(Task)
        _ * taskContainer.getByName(LifecycleBasePlugin.CHECK_TASK_NAME) >> check

        def binaryTasks = Mock(BinaryTasksCollection)
        def testTasks = Mock(BinaryTasksCollection)

        when:
        binary.tasks >> binaryTasks
        binaryTasks.withType(Test) >> testTasks

        and:
        plugin.wireTestTasksIntoCheckLifecycle(taskContainer, binaryContainer)

        then:
        1 * check.dependsOn(testTasks)
    }
}
