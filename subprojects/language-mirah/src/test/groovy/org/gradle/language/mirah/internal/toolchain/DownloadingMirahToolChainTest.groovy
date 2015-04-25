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

package org.gradle.language.mirah.internal.toolchain

import org.gradle.api.GradleException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ResolveException
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder
import org.gradle.api.internal.tasks.compile.daemon.CompilerDaemonManager
import org.gradle.api.internal.tasks.mirah.MirahCompileSpec
import org.gradle.internal.text.TreeFormatter
import org.gradle.language.mirah.MirahPlatform
import spock.lang.Specification

class DownloadingMirahToolChainTest extends Specification {

    ConfigurationContainer configurationContainer = Mock()
    CompilerDaemonManager compilerDaemonManager = Mock()
    DependencyHandler dependencyHandler = Mock()
    ProjectFinder projectFinder = Mock()
    DownloadingMirahToolChain mirahToolChain = new DownloadingMirahToolChain(projectFinder, compilerDaemonManager, configurationContainer, dependencyHandler)
    MirahPlatform mirahPlatform = Mock()

    def setup() {
        _ * mirahPlatform.getMirahVersion() >> "0.1.4"
    }

    def "tools available when compiler dependencies can be resolved"() {
        when:
        dependencyAvailable("mirah-compiler")
        then:
        mirahToolChain.select(mirahPlatform).isAvailable()
    }

    def "tools not available when compiler dependencies cannot be resolved"() {
        when:
        dependencyNotAvailable("mirah-compiler")
        def toolProvider = mirahToolChain.select(mirahPlatform)
        toolProvider.newCompiler(MirahCompileSpec.class)

        then:
        !toolProvider.isAvailable()
        TreeFormatter mirahcErrorFormatter = new TreeFormatter()
        toolProvider.explain(mirahcErrorFormatter)
        mirahcErrorFormatter.toString() == "Cannot provide Mirah Compiler: Cannot resolve 'mirah-compiler'."
        def e = thrown(GradleException)
        e.message == "Cannot provide Mirah Compiler: Cannot resolve 'mirah-compiler'."
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
