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

import org.gradle.api.GradleException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ResolveException
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.internal.file.FileResolver
import org.gradle.initialization.BuildGateToken
import org.gradle.internal.text.TreeFormatter
import org.gradle.language.scala.ScalaPlatform
import org.gradle.play.internal.toolchain.DefaultPlayToolChain
import org.gradle.play.internal.twirl.TwirlCompileSpec
import org.gradle.play.platform.PlayPlatform
import org.gradle.process.internal.worker.WorkerProcessFactory
import org.gradle.process.internal.worker.child.WorkerDirectoryProvider
import org.gradle.workers.internal.WorkerDaemonFactory
import spock.lang.Specification
import spock.lang.Unroll

class DefaultPlayToolChainTest extends Specification {
    FileResolver fileResolver = Mock()
    WorkerDaemonFactory workerDaemonFactory = Mock()
    ConfigurationContainer configurationContainer = Mock()
    DependencyHandler dependencyHandler = Mock()
    PlayPlatform playPlatform = Stub(PlayPlatform)
    WorkerProcessFactory workerProcessBuilderFactory = Mock()
    WorkerDirectoryProvider workerDirectoryProvider = Mock()
    BuildGateToken buildGate = Mock()
    def toolChain = new DefaultPlayToolChain(fileResolver, workerDaemonFactory, configurationContainer, dependencyHandler, workerProcessBuilderFactory, workerDirectoryProvider, buildGate)

    def setup() {
        playPlatform.playVersion >> DefaultPlayPlatform.DEFAULT_PLAY_VERSION
        playPlatform.scalaPlatform >> Stub(ScalaPlatform) {
            getScalaCompatibilityVersion() >> "2.10"
        }
    }

    def "provides meaningful name"() {
        expect:
        toolChain.getName() == "PlayToolchain"
    }

    def "provides meaningful displayname"() {
        expect:
        toolChain.getDisplayName() == "Default Play Toolchain"
    }

    def "can select toolprovider when dependencies are available"() {
        given:
        dependencyAvailable("twirl-compiler_2.10")
        dependencyAvailable("routes-compiler_2.10")
        dependencyAvailable("closure-compiler")

        when:
        def toolprovider = toolChain.select(playPlatform)

        then:
        toolprovider.isAvailable()
    }

    @Unroll
    def "cannot select toolprovider when #failedDependency is not available" () {
        given:
        dependencyAvailableIfNotFailed("twirl-compiler_2.10", failedDependency)
        dependencyAvailableIfNotFailed("routes-compiler_2.10", failedDependency)
        dependencyAvailableIfNotFailed("closure-compiler", failedDependency)

        when:
        def toolprovider = toolChain.select(playPlatform)

        then:
        !toolprovider.isAvailable()

        and:
        TreeFormatter formatter = new TreeFormatter()
        toolprovider.explain(formatter)
        formatter.toString() == "Cannot provide Play tool provider: Cannot resolve '${failedDependency}'."

        when:
        toolprovider.get(String.class)

        then:
        def e1 = thrown(GradleException)
        e1.message == "Cannot provide Play tool provider: Cannot resolve '${failedDependency}'."

        when:
        toolprovider.newCompiler(TwirlCompileSpec.class)

        then:
        def e2 = thrown(GradleException)
        e2.message == "Cannot provide Play tool provider: Cannot resolve '${failedDependency}'."

        where:
        failedDependency       | _
        "twirl-compiler_2.10"  | _
        "routes-compiler_2.10" | _
        "closure-compiler"     | _
    }

    private void dependencyAvailableIfNotFailed(String dependency, String failedDepenency) {
        if (dependency == failedDepenency) {
            dependencyNotAvailable(dependency)
        } else {
            dependencyAvailable(dependency)
        }
    }

    private void dependencyAvailable(String dependency) {
        Dependency someDependency = Mock()
        Configuration someConfiguration = Mock()
        (_..1) * dependencyHandler.create({ it =~ dependency }) >> someDependency
        (_..1) * configurationContainer.detachedConfiguration(someDependency) >> someConfiguration
        (_..1) * someConfiguration.resolve() >> new HashSet<File>()
    }

    private void dependencyNotAvailable(String dependency) {
        Dependency someDependency = Mock()
        ResolveException resolveException = Mock()
        Exception resolveExceptionCause = Mock()
        Configuration someConfiguration = Mock()

        _ * resolveException.cause >> resolveExceptionCause
        _ * resolveExceptionCause.getMessage() >> "Cannot resolve '$dependency'."
        1 * dependencyHandler.create({ it =~ dependency }) >> someDependency
        1 * configurationContainer.detachedConfiguration(someDependency) >> someConfiguration
        1 * someConfiguration.resolve() >> { throw resolveException }
    }
}
