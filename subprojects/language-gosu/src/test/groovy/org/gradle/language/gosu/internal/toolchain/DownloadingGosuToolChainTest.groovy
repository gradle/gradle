/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.language.gosu.internal.toolchain

import org.gradle.api.GradleException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ResolveException
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.internal.tasks.compile.daemon.CompilerDaemonManager
import org.gradle.api.internal.tasks.gosu.GosuCompileSpec
import org.gradle.internal.text.TreeFormatter
import org.gradle.language.gosu.GosuPlatform
import spock.lang.Specification

class DownloadingGosuToolChainTest extends Specification {

    ConfigurationContainer configurationContainer = Mock()
    CompilerDaemonManager compilerDaemonManager = Mock()
    DependencyHandler dependencyHandler = Mock()
    File gradleUserHome = Mock()
    File rootProjectDir = Mock()
    DownloadingGosuToolChain gosuToolChain = new DownloadingGosuToolChain(gradleUserHome, rootProjectDir, compilerDaemonManager, configurationContainer, dependencyHandler)
    GosuPlatform gosuPlatform = Mock()

    def setup() {
        _ * gosuPlatform.getGosuVersion() >> '1.13.1'
    }

    def 'tools available when compiler dependencies can be resolved'() {
        when:
        dependencyAvailable('gosu-ant-tools')
        then:
        gosuToolChain.select(gosuPlatform).isAvailable()
    }

    def 'tools not available when compiler dependencies cannot be resolved'() {
        when:
        dependencyNotAvailable('gosu-ant-tools')
        def toolProvider = gosuToolChain.select(gosuPlatform)
        toolProvider.newCompiler(GosuCompileSpec.class)

        then:
        !toolProvider.isAvailable()
        TreeFormatter gosucErrorFormatter = new TreeFormatter()
        toolProvider.explain(gosucErrorFormatter)
        gosucErrorFormatter.toString() == 'Cannot provide Gosu Compiler: Cannot resolve \'gosu-ant-tools\'.'
        def e = thrown(GradleException)
        e.message == 'Cannot provide Gosu Compiler: Cannot resolve \'gosu-ant-tools\'.'
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
