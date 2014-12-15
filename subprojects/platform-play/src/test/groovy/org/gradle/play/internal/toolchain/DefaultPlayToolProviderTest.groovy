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

package org.gradle.play.internal.toolchain
import org.gradle.api.InvalidUserDataException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.tasks.compile.daemon.CompilerDaemonManager
import org.gradle.internal.Factory
import org.gradle.language.base.internal.compile.CompileSpec
import org.gradle.language.scala.ScalaPlatform
import org.gradle.play.internal.run.PlayRunSpec
import org.gradle.play.platform.PlayPlatform
import org.gradle.process.internal.WorkerProcessBuilder
import spock.lang.Specification
import spock.lang.Unroll

class DefaultPlayToolProviderTest extends Specification {
    FileResolver fileResolver = Mock()
    CompilerDaemonManager compilerDaemonManager = Mock()
    ConfigurationContainer configurationContainer = Mock()
    DependencyHandler dependencyHandler = Mock()
    PlayPlatform playPlatform = Mock()
    ScalaPlatform scalaPlatform= Mock()

    DefaultPlayToolProvider playToolProvider
    Factory<WorkerProcessBuilder> workerProcessBuilderFactory = Mock()
    PlayRunSpec playRunSpec = Mock()

    @Unroll
    def "provides playRunner for play #playVersion"(){
        setup:
        def applicationFiles = Mock(FileCollection)
        1 * applicationFiles.getFiles() >> []
        1 * fileResolver.resolveFiles(_) >> applicationFiles
        _ * dependencyHandler.create(_)  >> Mock(Dependency);

        def runConfiguration = Mock(Configuration)

        _ * playPlatform.scalaPlatform >> scalaPlatform

        when:
        _ * playPlatform.getPlayVersion() >> playVersion
        playToolProvider = new DefaultPlayToolProvider(fileResolver, compilerDaemonManager, configurationContainer, dependencyHandler, playPlatform)
        def runner = playToolProvider.newApplicationRunner(workerProcessBuilderFactory, playRunSpec)

        then:
        runner != null
        1 * configurationContainer.detachedConfiguration(_) >> runConfiguration
        1 * runConfiguration.getFiles() >> []

        where:
        playVersion << ["2.2.x", "2.3.x"]
    }

    def "cannot create tool provider for unsupported play versions"() {
        when:
        _ * playPlatform.getPlayVersion() >> playVersion
        playToolProvider = new DefaultPlayToolProvider(fileResolver, compilerDaemonManager, configurationContainer, dependencyHandler, playPlatform)

        then: "fails with meaningful error message"
        def exception = thrown(InvalidUserDataException)
        exception.message == "Not a supported Play version: ${playVersion}. This plugin is compatible with: 2.3.x, 2.2.x"

        and: "no dependencies resolved"
        0 * dependencyHandler.create(_)
        0 * configurationContainer.detachedConfiguration(_)

        where:
        playVersion << ["2.1.x", "2.4.x", "3.0.0"]
    }

    def "newCompiler provides decent error for unsupported CompileSpec"() {
        setup:
        _ * playPlatform.getPlayVersion() >> "2.3.7"
        playToolProvider = new DefaultPlayToolProvider(fileResolver, compilerDaemonManager, configurationContainer, dependencyHandler, playPlatform)

        when:
        playToolProvider.newCompiler(new UnknownCompileSpec())

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message == "Cannot create Compiler for unsupported CompileSpec type 'UnknownCompileSpec'"
    }

}

class UnknownCompileSpec implements CompileSpec {}
