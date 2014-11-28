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
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.project.ProjectIdentifier
import org.gradle.api.tasks.testing.Test
import org.gradle.internal.service.ServiceRegistry
import org.gradle.language.scala.tasks.PlatformScalaCompile
import org.gradle.model.collection.CollectionBuilder
import org.gradle.platform.base.BinaryContainer
import org.gradle.play.PlayApplicationBinarySpec
import org.gradle.play.platform.PlayPlatform
import org.gradle.play.toolchain.PlayToolChain
import spock.lang.Specification
/**
 * Created by Rene on 19/11/14.
 */
class PlayApplicationPluginTest extends Specification {

    CollectionBuilder<Task> taskCollectionBuilder = Mock()
    BinaryContainer binaryContainer = Mock()
    ServiceRegistry serviceRegistry = Mock()
    ProjectIdentifier projectIdentifier = Mock()
    PlayApplicationBinarySpec binary = Mock()
    PlayPlatform playPlatform = Mock()
    PlayToolChain playToolChain = Mock()
    File buildDir = Mock()

    FileResolver fileResolver = Mock()
    ConfigurationContainer configurationContainer = Mock();
    DependencyHandler dependencyHandler = Mock();
    FileCollection fileCollection = Mock();

    PlayApplicationPlugin.Rules playApplicationPluginRules = new PlayApplicationPlugin.Rules()

    def setup(){
        1 * serviceRegistry.get(PlayToolChain.class) >> playToolChain
        1 * serviceRegistry.get(DependencyHandler.class) >> dependencyHandler
        1 * serviceRegistry.get(ConfigurationContainer.class) >> configurationContainer
        1 * serviceRegistry.get(FileResolver.class) >> fileResolver

        1 * binary.getTargetPlatform() >> playPlatform
        1 * binaryContainer.withType(PlayApplicationBinarySpec.class) >> binaryContainer
        1 * fileResolver.resolveFiles(_) >> fileCollection
    }


    def "adds test related tasks per binary"() {
        given:
        1 * binaryContainer.iterator() >> [binary].iterator()
        _ * binary.name >> "someBinary"

        when:
        playApplicationPluginRules.createTestTasks(taskCollectionBuilder, binaryContainer, serviceRegistry, projectIdentifier, buildDir)
        then:
        1 * taskCollectionBuilder.create("compileSomeBinaryTests", PlatformScalaCompile, _)
        1 * taskCollectionBuilder.create("testSomeBinary", Test, _)
        0 * taskCollectionBuilder.create(_)
        0 * taskCollectionBuilder.create(_, _, _)
    }
}
