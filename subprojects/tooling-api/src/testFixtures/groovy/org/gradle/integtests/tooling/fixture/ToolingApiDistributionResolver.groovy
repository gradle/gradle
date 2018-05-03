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
import org.gradle.api.artifacts.component.BuildIdentifier
import org.gradle.api.internal.StartParameterInternal
import org.gradle.api.internal.artifacts.DefaultBuildIdentifier
import org.gradle.api.internal.artifacts.DependencyResolutionServices
import org.gradle.initialization.BuildIdentity
import org.gradle.initialization.DefaultBuildCancellationToken
import org.gradle.initialization.DefaultBuildRequestMetaData
import org.gradle.initialization.GradleLauncherFactory
import org.gradle.initialization.NestedBuildFactory
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.concurrent.CompositeStoppable
import org.gradle.internal.concurrent.Stoppable
import org.gradle.internal.logging.LoggingManagerInternal
import org.gradle.internal.logging.services.LoggingServiceRegistry
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.service.ServiceRegistryBuilder
import org.gradle.internal.service.scopes.CrossBuildSessionScopeServices
import org.gradle.internal.service.scopes.BuildScopeServices
import org.gradle.internal.service.scopes.BuildSessionScopeServices
import org.gradle.internal.service.scopes.BuildTreeScopeServices
import org.gradle.internal.service.scopes.GlobalScopeServices
import org.gradle.internal.service.scopes.GradleUserHomeScopeServiceRegistry
import org.gradle.internal.service.scopes.ProjectScopeServices
import org.gradle.internal.time.Time
import org.gradle.internal.work.WorkerLeaseService
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.internal.NativeServicesTestFixture
import org.gradle.util.TestUtil

class ToolingApiDistributionResolver {
    private final DependencyResolutionServices resolutionServices
    private final Map<String, ToolingApiDistribution> distributions = [:]
    private final IntegrationTestBuildContext buildContext = new IntegrationTestBuildContext()
    private boolean useExternalToolingApiDistribution = false;
    private CompositeStoppable stopLater = new CompositeStoppable()

    ToolingApiDistributionResolver() {
        resolutionServices = createResolutionServices()
        resolutionServices.resolveRepositoryHandler.maven { url buildContext.libsRepo.toURI().toURL() }
    }

    ToolingApiDistributionResolver withRepository(String repositoryUrl) {
        resolutionServices.resolveRepositoryHandler.maven { url repositoryUrl }
        this
    }

    ToolingApiDistributionResolver withDefaultRepository() {
        withRepository("https://repo.gradle.org/gradle/repo")
    }

    ToolingApiDistribution resolve(String toolingApiVersion) {
        if (!distributions[toolingApiVersion]) {
            if (useToolingApiFromTestClasspath(toolingApiVersion)) {
                distributions[toolingApiVersion] = new TestClasspathToolingApiDistribution()
            } else {
                Dependency toolingApiDep = resolutionServices.dependencyHandler.create("org.gradle:gradle-tooling-api:$toolingApiVersion")
                Configuration toolingApiConfig = resolutionServices.configurationContainer.detachedConfiguration(toolingApiDep)
                distributions[toolingApiVersion] = new ExternalToolingApiDistribution(toolingApiVersion, toolingApiConfig.files)
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
        ServiceRegistry globalRegistry = ServiceRegistryBuilder.builder()
            .parent(LoggingServiceRegistry.newEmbeddableLogging())
            .parent(NativeServicesTestFixture.getInstance())
            .provider(new GlobalScopeServices(false))
            .build()
        def startParameter = new StartParameterInternal()
        startParameter.gradleUserHomeDir = new IntegrationTestBuildContext().gradleUserHomeDir
        def userHomeScopeServiceRegistry = globalRegistry.get(GradleUserHomeScopeServiceRegistry)
        def gradleUserHomeServices = userHomeScopeServiceRegistry.getServicesFor(startParameter.gradleUserHomeDir)
        def buildRequestMetadata = new DefaultBuildRequestMetaData(Time.currentTimeMillis())
        def crossBuildSessionScopeServices = new CrossBuildSessionScopeServices(globalRegistry, startParameter)
        def buildSessionServices = new BuildSessionScopeServices(gradleUserHomeServices, crossBuildSessionScopeServices, startParameter, buildRequestMetadata, ClassPath.EMPTY, new DefaultBuildCancellationToken())
        def buildTreeScopeServices = new BuildTreeScopeServices(buildSessionServices)
        def topLevelRegistry = new BuildScopeServices(buildTreeScopeServices)
        topLevelRegistry.add(BuildIdentity, new BuildIdentity() {
            @Override
            BuildIdentifier getCurrentBuild() {
                return new DefaultBuildIdentifier(":")
            }
        })
        topLevelRegistry.add(NestedBuildFactory, {} as NestedBuildFactory)
        def projectRegistry = new ProjectScopeServices(topLevelRegistry, TestUtil.create(TestNameTestDirectoryProvider.newInstance()).rootProject(), topLevelRegistry.getFactory(LoggingManagerInternal))

        def workerLeaseService = buildSessionServices.get(WorkerLeaseService)
        def workerLeaseCompletion = workerLeaseService.getWorkerLease().start()
        stopLater.add(new Stoppable() {
            @Override
            void stop() {
                workerLeaseCompletion.leaseFinish()
            }
        })

        stopLater.add(projectRegistry)
        stopLater.add(topLevelRegistry)
        stopLater.add(buildTreeScopeServices)
        stopLater.add(buildSessionServices)
        stopLater.add(new Stoppable() {
            @Override
            void stop() {
                userHomeScopeServiceRegistry.release(gradleUserHomeServices)
            }
        })
        stopLater.add(globalRegistry)

        // Need to load this early, since listener is registered in construction: otherwise it will be loaded in the middle of resolve
        projectRegistry.get(GradleLauncherFactory)
        return projectRegistry.get(DependencyResolutionServices)
    }

    ToolingApiDistributionResolver withExternalToolingApiDistribution() {
        this.useExternalToolingApiDistribution = true
        this
    }

    void stop() {
        stopLater.stop()
    }
}
