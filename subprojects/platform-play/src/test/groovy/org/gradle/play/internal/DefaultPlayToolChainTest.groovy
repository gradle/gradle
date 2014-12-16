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

package org.gradle.play.internal
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.tasks.compile.daemon.CompilerDaemonManager
import org.gradle.play.internal.toolchain.DefaultPlayToolChain
import org.gradle.play.platform.PlayPlatform
import spock.lang.Specification

class DefaultPlayToolChainTest extends Specification {

    FileResolver fileResolver = Mock()
    CompilerDaemonManager compilerDaemonManager = Mock()
    ConfigurationContainer configurationContainer = Mock()
    DependencyHandler dependencyHandler = Mock()
    PlayPlatform playPlatform = Mock()

    def setup() {
        _ * playPlatform.playVersion >> "2.3.7"
    }

    def "provides meaningful name"() {
        given:
        def toolChain = new DefaultPlayToolChain(fileResolver, compilerDaemonManager, configurationContainer, dependencyHandler)
        expect:
        toolChain.getName() == "PlayToolchain"
    }

    def "provides meaningful displayname"() {
        given:
        def toolChain = new DefaultPlayToolChain(fileResolver, compilerDaemonManager, configurationContainer, dependencyHandler)

        expect:
        toolChain.getDisplayName() == "Default Play Toolchain"
    }

    def "can select toolprovider"() {
        given:
        def toolChain = new DefaultPlayToolChain(fileResolver, compilerDaemonManager, configurationContainer, dependencyHandler)

        expect:
        toolChain.select(playPlatform) != null
    }
}
