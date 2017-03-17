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

package org.gradle.language.scala.internal.toolchain

import org.gradle.api.GradleException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ResolveException
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.internal.tasks.scala.ScalaCompileSpec
import org.gradle.internal.text.TreeFormatter
import org.gradle.language.scala.ScalaPlatform
import org.gradle.workers.WorkerExecutor
import spock.lang.Specification

class DownloadingScalaToolChainTest extends Specification {

    ConfigurationContainer configurationContainer = Mock()
    WorkerExecutor workerExecutor = Mock()
    DependencyHandler dependencyHandler = Mock()
    File gradleUserHome = Mock()
    DownloadingScalaToolChain scalaToolChain = new DownloadingScalaToolChain(gradleUserHome, workerExecutor, configurationContainer, dependencyHandler)
    ScalaPlatform scalaPlatform = Mock()

    def setup() {
        _ * scalaPlatform.getScalaVersion() >> "2.10.4"
    }

    def "tools available when compiler dependencies can be resolved"() {
        when:
        dependencyAvailable("scala-compiler")
        dependencyAvailable("zinc")
        then:
        scalaToolChain.select(scalaPlatform).isAvailable()
    }

    def "tools not available when compiler dependencies cannot be resolved"() {
        when:
        dependencyNotAvailable("scala-compiler")
        def toolProvider = scalaToolChain.select(scalaPlatform)
        toolProvider.newCompiler(ScalaCompileSpec.class)

        then:
        !toolProvider.isAvailable()
        TreeFormatter scalacErrorFormatter = new TreeFormatter()
        toolProvider.explain(scalacErrorFormatter)
        scalacErrorFormatter.toString() == "Cannot provide Scala Compiler: Cannot resolve 'scala-compiler'."
        def e = thrown(GradleException)
        e.message == "Cannot provide Scala Compiler: Cannot resolve 'scala-compiler'."

        when:
        dependencyAvailable("scala-compiler")
        dependencyNotAvailable("zinc")
        toolProvider = scalaToolChain.select(scalaPlatform)
        toolProvider.newCompiler(ScalaCompileSpec.class)

        then:
        def zincErrorFormatter = new TreeFormatter()
        !toolProvider.isAvailable()
        toolProvider.explain(zincErrorFormatter)
        zincErrorFormatter.toString() == "Cannot provide Scala Compiler: Cannot resolve 'zinc'."
        e = thrown(GradleException)
        e.message == "Cannot provide Scala Compiler: Cannot resolve 'zinc'."
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
