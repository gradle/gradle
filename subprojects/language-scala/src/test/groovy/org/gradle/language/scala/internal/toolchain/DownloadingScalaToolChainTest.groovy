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
import org.gradle.api.internal.ClassPathRegistry
import org.gradle.api.internal.tasks.scala.ScalaCompileSpec
import org.gradle.initialization.ClassLoaderRegistry
import org.gradle.internal.logging.text.TreeFormatter
import org.gradle.language.scala.ScalaPlatform
import org.gradle.process.internal.JavaForkOptionsFactory
import org.gradle.util.TextUtil
import org.gradle.workers.internal.ActionExecutionSpecFactory
import org.gradle.workers.internal.WorkerDaemonFactory
import spock.lang.Specification

class DownloadingScalaToolChainTest extends Specification {

    ConfigurationContainer configurationContainer = Mock()
    ClassPathRegistry classPathRegistry = Mock()
    ClassLoaderRegistry classLoaderRegistry = Mock()
    ActionExecutionSpecFactory actionExecutionSpecFactory = Mock()
    WorkerDaemonFactory workerDaemonFactory = Mock()
    DependencyHandler dependencyHandler = Mock()
    JavaForkOptionsFactory forkOptionsFactory = Mock()
    File gradleUserHome = Mock()
    File rootProjectDir = Mock()
    DownloadingScalaToolChain scalaToolChain = new DownloadingScalaToolChain(gradleUserHome, rootProjectDir, workerDaemonFactory, configurationContainer, dependencyHandler, forkOptionsFactory, classPathRegistry, classLoaderRegistry, actionExecutionSpecFactory)
    ScalaPlatform scalaPlatform = Mock()

    def setup() {
        _ * scalaPlatform.getScalaVersion() >> "2.10.4"
        _ * scalaPlatform.getScalaCompatibilityVersion() >> "2.10"
    }

    def "tools available when compiler dependencies can be resolved"() {
        Dependency scalaCompiler = Mock()
        Dependency compilerBridge = Mock()
        Dependency compilerInterface = Mock()
        Configuration scalaCompilerClasspath = Mock()

        Dependency zinc = Mock()
        Configuration zincClasspath = Mock()
        when:
        def toolProvider = scalaToolChain.select(scalaPlatform)
        then:

        1 * dependencyHandler.create({ it =~ "scala-compiler" }) >> scalaCompiler
        1 * dependencyHandler.create({ it =~ "compiler-bridge" }) >> compilerBridge
        1 * dependencyHandler.create({ it =~ "compiler-interface" }) >> compilerInterface
        1 * configurationContainer.detachedConfiguration(scalaCompiler, compilerBridge, compilerInterface) >> scalaCompilerClasspath
        1 * scalaCompilerClasspath.resolve() >> new HashSet<File>()

        1 * dependencyHandler.create({ it =~ "zinc" }) >> zinc
        1 * configurationContainer.detachedConfiguration(zinc) >> zincClasspath
        1 * zincClasspath.resolve() >> new HashSet<File>()

        toolProvider.isAvailable()
    }

    def "tools not available when compiler dependencies cannot be resolved"() {
        Dependency scalaCompiler = Mock()
        Dependency compilerBridge = Mock()
        Dependency compilerInterface = Mock()
        Configuration scalaCompilerClasspath = Mock()

        ResolveException resolveException = Mock()
        Exception resolveExceptionCause = Mock()
        resolveExceptionCause.getMessage() >> "Cannot resolve 'scala-compiler'."
        resolveException.cause >> resolveExceptionCause

        when:
        def toolProvider = scalaToolChain.select(scalaPlatform)
        toolProvider.newCompiler(ScalaCompileSpec.class)
        then:

        1 * dependencyHandler.create({ it =~ "scala-compiler" }) >> scalaCompiler
        1 * dependencyHandler.create({ it =~ "compiler-bridge" }) >> compilerBridge
        1 * dependencyHandler.create({ it =~ "compiler-interface" }) >> compilerInterface
        1 * configurationContainer.detachedConfiguration(scalaCompiler, compilerBridge, compilerInterface) >> scalaCompilerClasspath
        1 * scalaCompilerClasspath.resolve() >> { throw resolveException }

        !toolProvider.isAvailable()

        TreeFormatter scalacErrorFormatter = new TreeFormatter()
        toolProvider.explain(scalacErrorFormatter)
        scalacErrorFormatter.toString() == TextUtil.toPlatformLineSeparators("""Cannot provide Scala Compiler:
  - Cannot resolve 'scala-compiler'.""")

        and:
        def e = thrown(GradleException)
        e.message == TextUtil.toPlatformLineSeparators("""Cannot provide Scala Compiler:
  - Cannot resolve 'scala-compiler'.""")
    }
}
