/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.internal.cc.impl

import org.gradle.api.GradleException
import org.gradle.api.configuration.BuildFeatures
import org.gradle.api.internal.BuildType
import org.gradle.api.internal.StartParameterInternal
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.LocalComponentCache
import org.gradle.api.internal.configuration.DefaultBuildFeatures
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logging
import org.gradle.execution.selection.BuildTaskSelector
import org.gradle.initialization.Environment
import org.gradle.internal.build.BuildStateRegistry
import org.gradle.internal.buildtree.BuildActionModelRequirements
import org.gradle.internal.buildtree.BuildModelParameters
import org.gradle.internal.cc.buildtree.BuildModelParametersProvider
import org.gradle.internal.buildtree.BuildTreeLifecycleControllerFactory
import org.gradle.internal.buildtree.BuildTreeModelControllerServices
import org.gradle.internal.buildtree.BuildTreeModelSideEffectExecutor
import org.gradle.internal.buildtree.BuildTreeWorkGraphPreparer
import org.gradle.internal.buildtree.DefaultBuildTreeModelSideEffectExecutor
import org.gradle.internal.buildtree.DefaultBuildTreeWorkGraphPreparer
import org.gradle.internal.buildtree.RunTasksRequirements
import org.gradle.internal.cc.base.problems.IgnoringProblemsListener
import org.gradle.internal.cc.impl.barrier.BarrierAwareBuildTreeLifecycleControllerFactory
import org.gradle.internal.cc.impl.barrier.VintageConfigurationTimeActionRunner
import org.gradle.internal.cc.impl.fingerprint.ClassLoaderScopesFingerprintController
import org.gradle.internal.cc.impl.fingerprint.ConfigurationCacheClassLoaderScopesFingerprintController
import org.gradle.internal.cc.impl.fingerprint.ConfigurationCacheFingerprintController
import org.gradle.internal.cc.impl.fingerprint.ConfigurationCacheInputFileChecker
import org.gradle.internal.cc.impl.fingerprint.DefaultConfigurationCacheInputFileCheckerHost
import org.gradle.internal.cc.impl.fingerprint.IsolatedProjectsClassLoaderScopesFingerprintController
import org.gradle.internal.cc.impl.initialization.ConfigurationCacheInjectedClasspathInstrumentationStrategy
import org.gradle.internal.cc.impl.initialization.ConfigurationCacheProblemsListener
import org.gradle.internal.cc.impl.initialization.ConfigurationCacheStartParameter
import org.gradle.internal.cc.impl.initialization.DefaultConfigurationCacheProblemsListener
import org.gradle.internal.cc.impl.initialization.InstrumentedExecutionAccessListenerRegistry
import org.gradle.internal.cc.impl.initialization.VintageInjectedClasspathInstrumentationStrategy
import org.gradle.internal.cc.impl.models.DefaultToolingModelParameterCarrierFactory
import org.gradle.internal.cc.impl.problems.ConfigurationCacheProblems
import org.gradle.internal.cc.impl.promo.ConfigurationCachePromoHandler
import org.gradle.internal.cc.impl.promo.PromoInputsListener
import org.gradle.internal.cc.impl.services.ConfigurationCacheBuildTreeModelSideEffectExecutor
import org.gradle.internal.cc.impl.services.ConfigurationCacheEnvironment
import org.gradle.internal.cc.impl.services.DefaultDeferredRootBuildGradle
import org.gradle.internal.cc.impl.services.DefaultEnvironment
import org.gradle.internal.configuration.problems.DefaultProblemFactory
import org.gradle.internal.configuration.problems.ProblemFactory
import org.gradle.internal.scripts.ProjectScopedScriptResolution
import org.gradle.internal.serialize.codecs.core.jos.JavaSerializationEncodingLookup
import org.gradle.internal.service.Provides
import org.gradle.internal.service.ServiceRegistration
import org.gradle.internal.service.ServiceRegistrationProvider
import org.gradle.internal.snapshot.ValueSnapshotter
import org.gradle.plugin.use.resolve.service.internal.InjectedClasspathInstrumentationStrategy
import org.gradle.tooling.provider.model.internal.ToolingModelParameterCarrier
import org.gradle.util.internal.IncubationLogger


class DefaultBuildTreeModelControllerServices : BuildTreeModelControllerServices {

    private val logger = Logging.getLogger(BuildTreeModelControllerServices::class.java)

    override fun servicesForBuildTree(requirements: BuildActionModelRequirements): BuildTreeModelControllerServices.Supplier {
        val startParameter = requirements.startParameter

        // Isolated projects also implies configuration cache
        if (startParameter.isolatedProjects.get() && !startParameter.configurationCache.get()) {
            if (startParameter.configurationCache.isExplicit) {
                throw GradleException("The configuration cache cannot be disabled when isolated projects is enabled.")
            }
        }

        val configurationCacheLogLevel = if (startParameter.isConfigurationCacheQuiet) LogLevel.INFO else LogLevel.LIFECYCLE
        val modelParameters = BuildModelParametersProvider.parameters(requirements, startParameter, configurationCacheLogLevel)
        logger.info("Operational build model parameters: {}", modelParameters.toDisplayMap())

        if (modelParameters.isIsolatedProjects) {
            IncubationLogger.incubatingFeatureUsed("Isolated projects")
        } else {
            if (modelParameters.isConfigurationCacheParallelStore) {
                IncubationLogger.incubatingFeatureUsed("Parallel Configuration Cache")
            }
            if (modelParameters.isConfigureOnDemand) {
                IncubationLogger.incubatingFeatureUsed("Configuration on demand")
            }
        }

        val loggingParameters = ConfigurationCacheLoggingParameters(configurationCacheLogLevel)
        val buildFeatures = DefaultBuildFeatures(startParameter, modelParameters)

        return BuildTreeModelControllerServices.Supplier { registration ->
            val buildType = if (requirements.isRunsTasks) BuildType.TASKS else BuildType.MODEL
            registration.add(BuildType::class.java, buildType)
            registerCommonBuildTreeServices(registration, modelParameters, buildFeatures, requirements, loggingParameters)
        }
    }

    override fun servicesForNestedBuildTree(startParameter: StartParameterInternal): BuildTreeModelControllerServices.Supplier {
        val loggingParameters = ConfigurationCacheLoggingParameters(LogLevel.LIFECYCLE)
        return BuildTreeModelControllerServices.Supplier { registration ->
            registration.add(BuildType::class.java, BuildType.TASKS)
            val buildModelParameters = BuildModelParametersProvider.parametersForNestedBuildTree(startParameter)
            val buildFeatures = DefaultBuildFeatures(startParameter, buildModelParameters)
            val requirements = RunTasksRequirements(startParameter)
            registerCommonBuildTreeServices(registration, buildModelParameters, buildFeatures, requirements, loggingParameters)
        }
    }

    private
    fun registerCommonBuildTreeServices(
        registration: ServiceRegistration,
        modelParameters: BuildModelParameters,
        buildFeatures: DefaultBuildFeatures,
        requirements: BuildActionModelRequirements,
        loggingParameters: ConfigurationCacheLoggingParameters
    ) {
        registration.add(BuildModelParameters::class.java, modelParameters)
        registration.add(ConfigurationCacheLoggingParameters::class.java, loggingParameters)
        registration.add(BuildFeatures::class.java, buildFeatures)
        registration.add(BuildActionModelRequirements::class.java, requirements)
        registration.addProvider(SharedBuildTreeScopedServices())
        registration.add(JavaSerializationEncodingLookup::class.java)

        // This was originally only for the configuration cache, but now used for configuration cache and problems reporting
        registration.add(ProblemFactory::class.java, DefaultProblemFactory::class.java)

        registration.add(ConfigurationCacheProblemsListener::class.java, DefaultConfigurationCacheProblemsListener::class.java)
        // Set up CC problem reporting pipeline and promo, based on the build configuration
        when {
            // Collect and report problems. Don't suggest enabling CC if it is on, even if implicitly (e.g. enabled by isolated projects).
            // Most likely, the user who tries IP is already aware of CC and nudging will be just noise.
            modelParameters.isConfigurationCache -> registration.add(ConfigurationCacheProblems::class.java)
            // Allow nudging to enable CC if it is off and there is no explicit decision. CC doesn't work for model building so do not nudge there.
            !requirements.startParameter.configurationCache.isExplicit && !requirements.isCreatesModel -> registration.add(ConfigurationCachePromoHandler::class.java)
            // Do not nudge if CC is explicitly disabled or if models are requested.
            else -> registration.add(IgnoringProblemsListener::class.java, IgnoringProblemsListener)
        }

        if (modelParameters.isConfigurationCache) {
            registration.add(Environment::class.java, ConfigurationCacheEnvironment::class.java)
            registration.add(BuildTreeLifecycleControllerFactory::class.java, ConfigurationCacheBuildTreeLifecycleControllerFactory::class.java)
            registration.add(ConfigurationCacheStartParameter::class.java)
            registration.add(ConfigurationCacheClassLoaderScopeRegistryListener::class.java)
            registration.add(InjectedClasspathInstrumentationStrategy::class.java, ConfigurationCacheInjectedClasspathInstrumentationStrategy::class.java)
            registration.add(BuildTreeConfigurationCache::class.java, DefaultConfigurationCache::class.java)
            registration.add(InstrumentedExecutionAccessListenerRegistry::class.java)
            registration.add(ConfigurationCacheFingerprintController::class.java)
            registration.add(
                ConfigurationCacheInputFileChecker.Host::class.java,
                DefaultConfigurationCacheInputFileCheckerHost::class.java
            )
            if (modelParameters.isIsolatedProjects) {
                registration.add(
                    ClassLoaderScopesFingerprintController::class.java,
                    IsolatedProjectsClassLoaderScopesFingerprintController::class.java
                )
            } else {
                registration.add(
                    ClassLoaderScopesFingerprintController::class.java,
                    ConfigurationCacheClassLoaderScopesFingerprintController::class.java
                )
            }
            registration.addProvider(ConfigurationCacheBuildTreeProvider())
            registration.add(ConfigurationCacheBuildTreeModelSideEffectExecutor::class.java)
            registration.add(DefaultDeferredRootBuildGradle::class.java)
            registration.add(ConfigurationCacheInputsListener::class.java, InstrumentedInputAccessListener::class.java)
        } else {
            registration.add(Environment::class.java, DefaultEnvironment::class.java)
            registration.add(InjectedClasspathInstrumentationStrategy::class.java, VintageInjectedClasspathInstrumentationStrategy::class.java)
            registration.add(BuildTreeLifecycleControllerFactory::class.java, BarrierAwareBuildTreeLifecycleControllerFactory::class.java)
            registration.add(VintageConfigurationTimeActionRunner::class.java)
            registration.add(ProjectScopedScriptResolution::class.java, ProjectScopedScriptResolution.NO_OP)
            registration.addProvider(VintageBuildTreeProvider())
            registration.add(BuildTreeModelSideEffectExecutor::class.java, DefaultBuildTreeModelSideEffectExecutor::class.java)
            registration.add(ConfigurationCacheInputsListener::class.java, PromoInputsListener::class.java)
        }
        if (modelParameters.isIntermediateModelCache) {
            registration.addProvider(ConfigurationCacheModelProvider())
        } else {
            registration.addProvider(VintageModelProvider())
        }
    }

    private
    class SharedBuildTreeScopedServices : ServiceRegistrationProvider {
        @Provides
        fun createToolingModelParameterCarrierFactory(valueSnapshotter: ValueSnapshotter): ToolingModelParameterCarrier.Factory {
            return DefaultToolingModelParameterCarrierFactory(valueSnapshotter)
        }
    }

    private
    class ConfigurationCacheModelProvider : ServiceRegistrationProvider {
        @Provides
        fun createLocalComponentCache(cache: BuildTreeConfigurationCache): LocalComponentCache = ConfigurationCacheAwareLocalComponentCache(cache)
    }

    private
    class VintageModelProvider : ServiceRegistrationProvider {
        @Provides
        fun createLocalComponentCache(): LocalComponentCache = LocalComponentCache.NO_CACHE
    }

    private
    class ConfigurationCacheBuildTreeProvider : ServiceRegistrationProvider {
        @Provides
        fun createBuildTreeWorkGraphPreparer(buildRegistry: BuildStateRegistry, buildTaskSelector: BuildTaskSelector, cache: BuildTreeConfigurationCache): BuildTreeWorkGraphPreparer {
            return ConfigurationCacheAwareBuildTreeWorkGraphPreparer(DefaultBuildTreeWorkGraphPreparer(buildRegistry, buildTaskSelector), cache)
        }
    }

    private
    class VintageBuildTreeProvider : ServiceRegistrationProvider {
        @Provides
        fun createBuildTreeWorkGraphPreparer(buildRegistry: BuildStateRegistry, buildTaskSelector: BuildTaskSelector): BuildTreeWorkGraphPreparer {
            return DefaultBuildTreeWorkGraphPreparer(buildRegistry, buildTaskSelector)
        }
    }
}
