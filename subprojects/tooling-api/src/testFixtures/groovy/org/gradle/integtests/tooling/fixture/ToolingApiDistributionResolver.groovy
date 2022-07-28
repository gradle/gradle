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
import org.gradle.integtests.fixtures.executer.CommitDistribution
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testfixtures.internal.ProjectBuilderImpl

import javax.annotation.Nonnull

/**
 * Note that the resolver instance must be closed after use in order to release resources.
 */
class ToolingApiDistributionResolver implements AutoCloseable {

    private ProjectInternal project = null
    private DependencyResolutionServices resolutionServices = null

    private final Map<String, ToolingApiDistribution> distributions = [:]
    private final IntegrationTestBuildContext buildContext = new IntegrationTestBuildContext()
    private boolean useExternalToolingApiDistribution = false

    @Override
    void close() {
        if (project != null) {
            ProjectBuilderImpl.stop(project)
        }
    }

    ToolingApiDistributionResolver withRepository(String repositoryUrl) {
        getResolutionServices().resolveRepositoryHandler.maven { url repositoryUrl }
        this
    }

    ToolingApiDistributionResolver withDefaultRepository() {
        withRepository(RepoScriptBlockUtil.gradleRepositoryMirrorUrl())
    }

    ToolingApiDistributionResolver withExternalToolingApiDistribution() {
        this.useExternalToolingApiDistribution = true
        this
    }

    ToolingApiDistribution resolve(String toolingApiVersion) {
        if (!distributions[toolingApiVersion]) {
            if (useToolingApiFromTestClasspath(toolingApiVersion)) {
                distributions[toolingApiVersion] = new TestClasspathToolingApiDistribution()
            } else if (CommitDistribution.isCommitDistribution(toolingApiVersion)) {
                File toolingApiJar = CommitDistribution.getToolingApiJar(toolingApiVersion)
                List<File> slf4j = resolveDependency("org.slf4j:slf4j-api:1.7.25").toList()
                distributions[toolingApiVersion] = new ExternalToolingApiDistribution(toolingApiVersion, slf4j + toolingApiJar)
            } else {
                distributions[toolingApiVersion] = new ExternalToolingApiDistribution(toolingApiVersion, resolveDependency("org.gradle:gradle-tooling-api:$toolingApiVersion"))
            }
        }
        distributions[toolingApiVersion]
    }

    private Collection<File> resolveDependency(String dependency) {
        Dependency dep = getResolutionServices().dependencyHandler.create(dependency)
        Configuration config = getResolutionServices().configurationContainer.detachedConfiguration(dep)
        config.resolutionStrategy.disableDependencyVerification()
        return config.files
    }

    private boolean useToolingApiFromTestClasspath(String toolingApiVersion) {
        !useExternalToolingApiDistribution &&
            toolingApiVersion == buildContext.version.baseVersion.version
    }

    @Nonnull
    private DependencyResolutionServices getResolutionServices() {
        if (this.resolutionServices == null) {
            this.resolutionServices = createResolutionServices()
        }
        return this.resolutionServices
    }

    @Nonnull
    private DependencyResolutionServices createResolutionServices() {
        def resolutionServices = getProject().services.get(DependencyResolutionServices)
        def localRepository = buildContext.localRepository
        if (localRepository) {
            resolutionServices.resolveRepositoryHandler.maven { url localRepository.toURI().toURL() }
        }
        return resolutionServices
    }

    @Nonnull
    private ProjectInternal getProject() {
        if (project == null) {
            project = (ProjectInternal) ProjectBuilder.builder().build()
        }
        return project
    }
}
