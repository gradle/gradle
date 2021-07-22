/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.configurationcache

import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.project.CrossProjectModelAccess
import org.gradle.api.internal.project.DefaultCrossProjectModelAccess
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectRegistry
import org.gradle.configuration.ProjectsPreparer
import org.gradle.configuration.internal.UserCodeApplicationContext
import org.gradle.configurationcache.build.ConfigurationCacheIncludedBuildState
import org.gradle.configurationcache.build.NoOpBuildModelController
import org.gradle.configurationcache.extensions.get
import org.gradle.configurationcache.fingerprint.ConfigurationCacheFingerprintController
import org.gradle.configurationcache.initialization.ConfigurationCacheBuildEnablement
import org.gradle.configurationcache.initialization.ConfigurationCacheStartParameter
import org.gradle.configurationcache.initialization.DefaultConfigurationCacheProblemsListener
import org.gradle.configurationcache.initialization.DefaultInjectedClasspathInstrumentationStrategy
import org.gradle.configurationcache.problems.ConfigurationCacheProblems
import org.gradle.configurationcache.problems.ConfigurationCacheReport
import org.gradle.configurationcache.problems.ProblemsListener
import org.gradle.configurationcache.serialization.beans.BeanConstructors
import org.gradle.execution.DefaultTaskSchedulingPreparer
import org.gradle.execution.ExcludedTaskFilteringProjectsPreparer
import org.gradle.initialization.SettingsPreparer
import org.gradle.initialization.TaskExecutionPreparer
import org.gradle.initialization.VintageBuildModelController
import org.gradle.internal.build.BuildModelController
import org.gradle.internal.build.BuildState
import org.gradle.internal.buildtree.BuildModelParameters
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.service.ServiceRegistration
import org.gradle.internal.service.scopes.AbstractPluginServiceRegistry
import org.gradle.internal.service.scopes.Scopes
import org.gradle.internal.service.scopes.ServiceScope


class ConfigurationCacheServices : AbstractPluginServiceRegistry() {
    override fun registerGlobalServices(registration: ServiceRegistration) {
        registration.run {
            add(BeanConstructors::class.java)
        }
    }

    override fun registerBuildSessionServices(registration: ServiceRegistration) {
        registration.run {
            add(DefaultBuildTreeModelControllerServices::class.java)
        }
    }

    override fun registerBuildTreeServices(registration: ServiceRegistration) {
        registration.run {
            add(BuildTreeListenerManager::class.java)
            add(ConfigurationCacheStartParameter::class.java)
            add(DefaultInjectedClasspathInstrumentationStrategy::class.java)
            add(ConfigurationCacheKey::class.java)
            add(ConfigurationCacheReport::class.java)
            add(ConfigurationCacheProblems::class.java)
            add(ConfigurationCacheClassLoaderScopeRegistryListener::class.java)
            add(DefaultConfigurationCacheProblemsListener::class.java)
            add(DefaultBuildTreeLifecycleControllerFactory::class.java)
            add(ConfigurationCacheRepository::class.java)
            add(DefaultConfigurationCache::class.java)
        }
    }

    override fun registerBuildServices(registration: ServiceRegistration) {
        registration.run {
            add(ConfigurationCacheBuildEnablement::class.java)
            add(ConfigurationCacheProblemsListenerManagerAction::class.java)
            add(SystemPropertyAccessListener::class.java)
            add(RelevantProjectsRegistry::class.java)
            add(ConfigurationCacheFingerprintController::class.java)
            addProvider(BuildScopeServicesProvider())
        }
    }

    override fun registerGradleServices(registration: ServiceRegistration) {
        registration.run {
            add(ConfigurationCacheHost::class.java)
            add(ConfigurationCacheIO::class.java)
        }
    }

    class BuildScopeServicesProvider {
        fun createBuildModelController(
            build: BuildState,
            gradle: GradleInternal,
            startParameter: ConfigurationCacheStartParameter,
            configurationCache: BuildTreeConfigurationCache
        ): BuildModelController {
            if (build is ConfigurationCacheIncludedBuildState) {
                return NoOpBuildModelController(gradle)
            }
            val projectsPreparer: ProjectsPreparer = gradle.services.get()
            val taskSchedulingPreparer = DefaultTaskSchedulingPreparer(gradle.services.get(), ExcludedTaskFilteringProjectsPreparer(gradle.services.get()))
            val settingsPreparer: SettingsPreparer = gradle.services.get()
            val taskExecutionPreparer: TaskExecutionPreparer = gradle.services.get()
            val vintageController = VintageBuildModelController(gradle, projectsPreparer, taskSchedulingPreparer, settingsPreparer, taskExecutionPreparer)
            return if (startParameter.isEnabled) {
                ConfigurationCacheAwareBuildModelController(gradle, vintageController, configurationCache)
            } else {
                vintageController
            }
        }

        fun createCrossProjectModelAccess(
            projectRegistry: ProjectRegistry<ProjectInternal>,
            modelParameters: BuildModelParameters,
            problemsListener: ProblemsListener,
            userCodeApplicationContext: UserCodeApplicationContext
        ): CrossProjectModelAccess {
            val delegate = DefaultCrossProjectModelAccess(projectRegistry)
            return if (modelParameters.isIsolatedProjects) {
                ProblemReportingCrossProjectModelAccess(delegate, problemsListener, userCodeApplicationContext)
            } else {
                delegate
            }
        }
    }
}


@ServiceScope(Scopes.BuildTree::class)
class BuildTreeListenerManager(
    val service: ListenerManager
)
