/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.integtests.tooling.fixture

import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.internal.artifacts.DependencyResolutionServices
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.integtests.fixtures.RepoScriptBlockUtil
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.integtests.fixtures.executer.LocallyBuiltGradleDistribution
import org.gradle.testfixtures.ProjectBuilder

class ToolingApiDistributionResolver {
    private final DependencyResolutionServices resolutionServices
    private final Map<String, ToolingApiDistribution> distributions = [:]
    private final IntegrationTestBuildContext buildContext = new IntegrationTestBuildContext()
    private boolean useExternalToolingApiDistribution = false

    ToolingApiDistributionResolver() {
        resolutionServices = createResolutionServices()
        def localRepository = buildContext.localRepository
        if (localRepository) {
            resolutionServices.resolveRepositoryHandler.maven { url localRepository.toURI().toURL() }
        }
    }

    ToolingApiDistributionResolver withRepository(String repositoryUrl) {
        resolutionServices.resolveRepositoryHandler.maven { url repositoryUrl }
        this
    }

    ToolingApiDistributionResolver withDefaultRepository() {
        withRepository(RepoScriptBlockUtil.gradleRepositoryMirrorUrl())
    }

    ToolingApiDistribution resolve(String toolingApiVersion) {
        if (!distributions[toolingApiVersion]) {
            if (useToolingApiFromTestClasspath(toolingApiVersion)) {
                distributions[toolingApiVersion] = new TestClasspathToolingApiDistribution()
            } else if (LocallyBuiltGradleDistribution.isLocallyBuiltVersion(toolingApiVersion)) {
                File toolingApiJar = LocallyBuiltGradleDistribution.getToolingApiJar(toolingApiVersion)
                List<File> slf4j = resolveDependency("org.slf4j:slf4j-api:1.7.25").toList()
                distributions[toolingApiVersion] = new ExternalToolingApiDistribution(toolingApiVersion, slf4j + toolingApiJar)
            } else {
                distributions[toolingApiVersion] = new ExternalToolingApiDistribution(toolingApiVersion, resolveDependency("org.gradle:gradle-tooling-api:$toolingApiVersion"))
            }
        }
        distributions[toolingApiVersion]
    }

    private Collection<File> resolveDependency(String dependency) {
        Dependency dep = resolutionServices.dependencyHandler.create(dependency)
        Configuration config = resolutionServices.configurationContainer.detachedConfiguration(dep)
        config.resolutionStrategy.disableDependencyVerification()
        return config.files
    }

    private boolean useToolingApiFromTestClasspath(String toolingApiVersion) {
        !useExternalToolingApiDistribution &&
            toolingApiVersion == buildContext.version.baseVersion.version
    }

    private DependencyResolutionServices createResolutionServices() {
        // Create a dummy project and use its services
        ProjectInternal project = ProjectBuilder.builder().build()
        return project.services.get(DependencyResolutionServices)
    }

    ToolingApiDistributionResolver withExternalToolingApiDistribution() {
        this.useExternalToolingApiDistribution = true
        this
    }
}
