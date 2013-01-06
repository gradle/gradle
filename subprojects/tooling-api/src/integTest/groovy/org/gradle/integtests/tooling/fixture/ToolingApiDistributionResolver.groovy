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

import org.gradle.StartParameter
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.internal.artifacts.DependencyResolutionServices
import org.gradle.api.internal.project.GlobalServicesRegistry
import org.gradle.api.internal.project.ProjectInternalServiceRegistry
import org.gradle.api.internal.project.TopLevelBuildServiceRegistry
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.util.HelperUtil

class ToolingApiDistributionResolver {
    private final DependencyResolutionServices resolutionServices
    private final Map<String, ToolingApiDistribution> distributions = [:]
    private final IntegrationTestBuildContext buildContext = new IntegrationTestBuildContext()
    private boolean useExternalToolingApiDistribution = false;

    ToolingApiDistributionResolver() {
        resolutionServices = createResolutionServices()
        resolutionServices.resolveRepositoryHandler.maven { url buildContext.libsRepo.toURI().toURL() }
    }

    ToolingApiDistributionResolver withRepository(String repositoryUrl) {
        resolutionServices.resolveRepositoryHandler.maven { url repositoryUrl }
        this
    }

    ToolingApiDistributionResolver withDefaultRepository() {
        withRepository("http://repo.gradle.org/gradle/repo")
    }

    ToolingApiDistribution resolve(String toolingApiVersion) {
        if (!distributions[toolingApiVersion]) {
            if (useToolingApiFromTestClasspath(toolingApiVersion)) {
                distributions[toolingApiVersion] = new TestClasspathToolingApiDistribution()
            } else {
                Dependency toolingApiDep = resolutionServices.dependencyHandler.create("org.gradle:gradle-tooling-api:$toolingApiVersion")
                Configuration toolingApiConfig = resolutionServices.configurationContainer.detachedConfiguration(toolingApiDep)
                distributions[toolingApiVersion] = new ExternalToolingApiDistribution(toolingApiVersion, toolingApiConfig)
            }
        }
        distributions[toolingApiVersion]
    }

    private boolean useToolingApiFromTestClasspath(String toolingApiVersion) {
        !useExternalToolingApiDistribution &&
        toolingApiVersion == buildContext.version.version &&
                GradleContextualExecuter.embedded
    }

    private DependencyResolutionServices createResolutionServices() {
        GlobalServicesRegistry globalRegistry = new GlobalServicesRegistry()
        StartParameter startParameter = new StartParameter()
        startParameter.gradleUserHomeDir = new IntegrationTestBuildContext().gradleUserHomeDir
        TopLevelBuildServiceRegistry topLevelRegistry = new TopLevelBuildServiceRegistry(globalRegistry, startParameter)
        ProjectInternalServiceRegistry projectRegistry = new ProjectInternalServiceRegistry(topLevelRegistry, HelperUtil.createRootProject())
        projectRegistry.get(DependencyResolutionServices)
    }

    ToolingApiDistributionResolver withExternalToolingApiDistribution() {
        this.useExternalToolingApiDistribution = true
        this
    }
}
