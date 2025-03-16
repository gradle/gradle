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
import org.gradle.integtests.fixtures.executer.CommitDistribution
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.internal.exceptions.DefaultMultiCauseException
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testfixtures.internal.ProjectBuilderImpl

class ToolingApiDistributionResolver {

    static interface ResolverAction<T> {
        T run(ToolingApiDistributionResolver resolver)
    }

    /**
     * Executes given {@code block} against a fresh instance of the {@code ToolingApiDistributionResolver}
     * and returns the result.
     */
    static <T> T use(ResolverAction<T> block) {
        def project = (ProjectInternal) ProjectBuilder.builder().build()
        try {
            def resolver = new ToolingApiDistributionResolver(project)
            return block.run(resolver)
        } finally {
            ProjectBuilderImpl.stop(project)
        }
    }

    private final ProjectInternal project
    private final DependencyResolutionServices resolutionServices

    private final Map<String, ToolingApiDistribution> distributions = [:]
    private final IntegrationTestBuildContext buildContext = new IntegrationTestBuildContext()
    private boolean useExternalToolingApiDistribution = false

    private ToolingApiDistributionResolver(ProjectInternal project) {
        this.project = project
        this.resolutionServices = project.services.get(DependencyResolutionServices)
        def localRepository = buildContext.localRepository
        if (localRepository) {
            this.resolutionServices.resolveRepositoryHandler.maven { url = localRepository.toURI() }
        }
    }

    ToolingApiDistributionResolver withRepository(String repositoryUrl) {
        resolutionServices.resolveRepositoryHandler.maven { url = repositoryUrl }
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
        LinkedList<Integer> retryMillis = [1000, 2000, 4000] as LinkedList
        List<Throwable> exceptions = []
        do {
            try {
                Dependency dep = resolutionServices.dependencyHandler.create(dependency)
                Configuration config = resolutionServices.configurationContainer.detachedConfiguration(dep)
                config.resolutionStrategy.disableDependencyVerification()
                return config.files
            } catch (Throwable t) {
                exceptions.add(t)
                Thread.sleep(retryMillis.removeFirst())
            }
        } while (!retryMillis.isEmpty())

        throw new DefaultMultiCauseException("Failed to resolve $dependency", exceptions)
    }

    private boolean useToolingApiFromTestClasspath(String toolingApiVersion) {
        !useExternalToolingApiDistribution &&
            toolingApiVersion == buildContext.version.baseVersion.version
    }
}
