/*
 * Copyright 2026 the original author or authors.
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

import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.artifacts.ivyservice.ArtifactCachesProvider
import org.gradle.api.internal.artifacts.ivyservice.modulecache.FileStoreAndIndexProvider
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.LocalComponentCache
import org.gradle.api.internal.file.temp.TemporaryFileProvider
import org.gradle.api.internal.project.CrossProjectModelAccess
import org.gradle.api.internal.project.DefaultCrossProjectModelAccess
import org.gradle.api.internal.project.DefaultDynamicLookupRoutine
import org.gradle.api.internal.project.DynamicLookupRoutine
import org.gradle.api.internal.project.ProjectRegistry
import org.gradle.api.internal.provider.ConfigurationTimeBarrier
import org.gradle.api.internal.tasks.TaskExecutionAccessChecker
import org.gradle.api.internal.tasks.execution.TaskExecutionAccessListener
import org.gradle.configuration.ProjectsPreparer
import org.gradle.configuration.ScriptPluginFactory
import org.gradle.configuration.internal.DefaultDynamicCallContextTracker
import org.gradle.configuration.internal.DynamicCallContextTracker
import org.gradle.configuration.project.BuildScriptProcessor
import org.gradle.configuration.project.ConfigureActionsProjectEvaluator
import org.gradle.configuration.project.DelayedConfigurationActions
import org.gradle.configuration.project.LifecycleProjectEvaluator
import org.gradle.configuration.project.PluginsProjectConfigureActions
import org.gradle.configuration.project.ProjectEvaluator
import org.gradle.execution.ExecutionAccessChecker
import org.gradle.execution.ExecutionAccessListener
import org.gradle.execution.selection.BuildTaskSelector
import org.gradle.initialization.BuildCancellationToken
import org.gradle.initialization.Environment
import org.gradle.initialization.SettingsPreparer
import org.gradle.initialization.TaskExecutionPreparer
import org.gradle.initialization.VintageBuildModelController
import org.gradle.internal.build.BuildModelController
import org.gradle.internal.build.BuildStateRegistry
import org.gradle.internal.build.BuildToolingModelControllerFactory
import org.gradle.internal.buildoption.InternalOptions
import org.gradle.internal.buildtree.BuildModelParameters
import org.gradle.internal.buildtree.BuildTreeLifecycleControllerFactory
import org.gradle.internal.buildtree.BuildTreeModelSideEffectExecutor
import org.gradle.internal.buildtree.BuildTreeWorkGraphPreparer
import org.gradle.internal.buildtree.DefaultBuildTreeModelSideEffectExecutor
import org.gradle.internal.buildtree.DefaultBuildTreeWorkGraphPreparer
import org.gradle.internal.cc.base.services.ProjectRefResolver
import org.gradle.internal.cc.impl.barrier.BarrierAwareBuildTreeLifecycleControllerFactory
import org.gradle.internal.cc.impl.barrier.VintageConfigurationTimeActionRunner
import org.gradle.internal.cc.impl.fingerprint.ClassLoaderScopesFingerprintController
import org.gradle.internal.cc.impl.fingerprint.ConfigurationCacheClassLoaderScopesFingerprintController
import org.gradle.internal.cc.impl.fingerprint.ConfigurationCacheFingerprintController
import org.gradle.internal.cc.impl.fingerprint.ConfigurationCacheFingerprintEventHandler
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
import org.gradle.internal.cc.impl.problems.BuildNameProvider
import org.gradle.internal.cc.impl.promo.PromoInputsListener
import org.gradle.internal.cc.impl.serialize.ConfigurationCacheCodecs
import org.gradle.internal.cc.impl.serialize.DefaultConfigurationCacheCodecs
import org.gradle.internal.cc.impl.services.ConfigurationCacheBuildTreeModelSideEffectExecutor
import org.gradle.internal.cc.impl.services.ConfigurationCacheEnvironment
import org.gradle.internal.cc.impl.services.DefaultDeferredRootBuildGradle
import org.gradle.internal.cc.impl.services.DefaultEnvironment
import org.gradle.internal.cc.impl.services.RemoteScriptUpToDateChecker
import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.configuration.problems.CommonReport
import org.gradle.internal.configuration.problems.DefaultProblemFactory
import org.gradle.internal.configuration.problems.ProblemFactory
import org.gradle.internal.configuration.problems.ProblemsListener
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.execution.WorkExecutionTracker
import org.gradle.internal.model.StateTransitionControllerFactory
import org.gradle.internal.nativeintegration.filesystem.FileSystem
import org.gradle.internal.operations.BuildOperationRunner
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.resource.connector.ResourceConnectorFactory
import org.gradle.internal.resource.connector.ResourceConnectorSpecification
import org.gradle.internal.resource.transfer.ExternalResourceConnector
import org.gradle.internal.scripts.ProjectScopedScriptResolution
import org.gradle.internal.serialize.codecs.core.jos.JavaSerializationEncodingLookup
import org.gradle.internal.service.CachingServiceLocator
import org.gradle.internal.service.PrivateService
import org.gradle.internal.service.Provides
import org.gradle.internal.service.ServiceRegistration
import org.gradle.internal.service.ServiceRegistrationProvider
import org.gradle.internal.service.scopes.Scope
import org.gradle.internal.service.scopes.ServiceScope
import org.gradle.internal.snapshot.ValueSnapshotter
import org.gradle.invocation.GradleLifecycleActionExecutor
import org.gradle.plugin.use.resolve.service.internal.InjectedClasspathInstrumentationStrategy
import org.gradle.tooling.provider.model.internal.ToolingModelParameterCarrier
import java.io.File


/**
 * Services implementations shared across all modes (Vintage, CC, IP)
 */
internal interface BaseGradleModeServices {

    /**
     * Build-tree-scoped service implementations shared across all modes
     */
    @ServiceScope(Scope.BuildTree::class)
    abstract class BuildTree : ServiceRegistrationProvider {
        open fun configure(registration: ServiceRegistration) {
            registration.add(BuildNameProvider::class.java)
            registration.add(ConfigurationCacheKey::class.java)
            registration.add(BuildToolingModelControllerFactory::class.java, DefaultBuildToolingModelControllerFactory::class.java)
            registration.add(DeprecatedFeaturesListener::class.java)
            registration.add(InputTrackingState::class.java)
            registration.add(InstrumentedExecutionAccessListener::class.java)
            registration.add(DefaultConfigurationCacheDegradationController::class.java)
            registration.add(ConfigurationCacheFingerprintEventHandler::class.java)

            registration.add(JavaSerializationEncodingLookup::class.java)

            // This was originally only for the configuration cache, but now used for configuration cache and problems reporting
            registration.add(ProblemFactory::class.java, DefaultProblemFactory::class.java)

            registration.add(ConfigurationCacheProblemsListener::class.java, DefaultConfigurationCacheProblemsListener::class.java)
        }

        @Provides
        fun createToolingModelParameterCarrierFactory(valueSnapshotter: ValueSnapshotter): ToolingModelParameterCarrier.Factory {
            return DefaultToolingModelParameterCarrierFactory(valueSnapshotter)
        }

        @Provides
        fun createIgnoredConfigurationInputs(
            configurationCacheStartParameter: ConfigurationCacheStartParameter,
            fileSystem: FileSystem
        ): IgnoredConfigurationInputs =
            if (hasIgnoredPaths(configurationCacheStartParameter))
                DefaultIgnoredConfigurationInputs(configurationCacheStartParameter, fileSystem)
            else object : IgnoredConfigurationInputs {
                override fun isFileSystemCheckIgnoredFor(file: File): Boolean = false
            }

        private fun hasIgnoredPaths(configurationCacheStartParameter: ConfigurationCacheStartParameter): Boolean =
            !configurationCacheStartParameter.ignoredFileSystemCheckInputs.isNullOrEmpty()

        @Provides
        fun createRemoteScriptUpToDateChecker(
            artifactCachesProvider: ArtifactCachesProvider,
            startParameter: ConfigurationCacheStartParameter,
            temporaryFileProvider: TemporaryFileProvider,
            fileStoreAndIndexProvider: FileStoreAndIndexProvider,
            resourceConnectorFactories: List<ResourceConnectorFactory>
        ): RemoteScriptUpToDateChecker =
            artifactCachesProvider.withWritableCache { _, cacheLockingManager ->
                RemoteScriptUpToDateChecker(
                    cacheLockingManager,
                    startParameter,
                    temporaryFileProvider,
                    fileStoreAndIndexProvider.externalResourceFileStore,
                    httpResourceConnectorFrom(resourceConnectorFactories),
                    fileStoreAndIndexProvider.externalResourceIndex
                )
            }

        private fun httpResourceConnectorFrom(resourceConnectorFactories: List<ResourceConnectorFactory>): ExternalResourceConnector =
            resourceConnectorFactories
                .single { "https" in it.supportedProtocols }
                .createResourceConnector(object : ResourceConnectorSpecification {})

        @Provides
        fun createConfigurationCacheReport(
            executorFactory: ExecutorFactory,
            temporaryFileProvider: TemporaryFileProvider,
            internalOptions: InternalOptions
        ): CommonReport {
            return CommonReport(executorFactory, temporaryFileProvider, internalOptions, "configuration cache report", "configuration-cache-report")
        }
    }

    /**
     * Build-scoped services implementations shared across all modes
     */
    @ServiceScope(Scope.Build::class)
    abstract class Build : ServiceRegistrationProvider {

        open fun configure(registration: ServiceRegistration) {
            registration.run {
                add(RelevantProjectsRegistry::class.java)
                add(ConfigurationCacheHost::class.java, DefaultConfigurationCacheHost::class.java)
                add(ConfigurationCacheCodecs::class.java, DefaultConfigurationCacheCodecs::class.java)
                add(
                    ConfigurationCacheBuildTreeIO::class.java,
                    ConfigurationCacheIncludedBuildIO::class.java,
                    DefaultConfigurationCacheIO::class.java
                )
            }
        }

        @Provides
        fun createTaskExecutionAccessChecker(
            /** In non-CC builds, [BuildTreeConfigurationCache] is not registered; accepting a list here is a way to ignore its absence. */
            configurationCache: List<BuildTreeConfigurationCache>,
            configurationTimeBarrier: ConfigurationTimeBarrier,
            modelParameters: BuildModelParameters,
            /** In non-CC builds, [ConfigurationCacheStartParameter] is not registered; accepting a list here is a way to ignore its absence. */
            configurationCacheStartParameter: List<ConfigurationCacheStartParameter>,
            listenerManager: ListenerManager,
            workExecutionTracker: WorkExecutionTracker,
        ): TaskExecutionAccessChecker {
            val broadcast = listenerManager.getBroadcaster(TaskExecutionAccessListener::class.java)
            val workGraphLoadingState = workGraphLoadingStateFrom(configurationCache)
            return when {
                !modelParameters.isConfigurationCache -> TaskExecutionAccessCheckers.TaskStateBased(workGraphLoadingState, broadcast, workExecutionTracker)
                configurationCacheStartParameter.single().taskExecutionAccessPreStable -> TaskExecutionAccessCheckers.TaskStateBased(
                    workGraphLoadingState,
                    broadcast,
                    workExecutionTracker
                )

                else -> TaskExecutionAccessCheckers.ConfigurationTimeBarrierBased(configurationTimeBarrier, workGraphLoadingState, broadcast, workExecutionTracker)
            }
        }

        private
        fun workGraphLoadingStateFrom(maybeConfigurationCache: List<BuildTreeConfigurationCache>): WorkGraphLoadingState {
            if (maybeConfigurationCache.isEmpty()) {
                return WorkGraphLoadingState { false }
            }

            val configurationCache = maybeConfigurationCache.single()
            return WorkGraphLoadingState { configurationCache.isLoaded }
        }

        @Provides
        @PrivateService
        fun createLifecycleProjectEvaluator(
            buildOperationRunner: BuildOperationRunner,
            cachingServiceLocator: CachingServiceLocator,
            scriptPluginFactory: ScriptPluginFactory,
            cancellationToken: BuildCancellationToken
        ): LifecycleProjectEvaluator {
            val withActionsEvaluator = ConfigureActionsProjectEvaluator(
                PluginsProjectConfigureActions.from(cachingServiceLocator),
                BuildScriptProcessor(scriptPluginFactory),
                DelayedConfigurationActions()
            )
            return LifecycleProjectEvaluator(buildOperationRunner, withActionsEvaluator, cancellationToken)
        }
    }
}

/**
 * Service implementations used for Vintage mode
 */
internal interface VintageGradleModeServices {

    /**
     * Build-tree-scoped service implementations used for Vintage mode
     */
    @ServiceScope(Scope.BuildTree::class)
    object BuildTree : BaseGradleModeServices.BuildTree() {
        override fun configure(registration: ServiceRegistration) {
            super.configure(registration)

            // region ALL MODES
            registration.add(Environment::class.java, DefaultEnvironment::class.java)
            registration.add(BuildTreeLifecycleControllerFactory::class.java, BarrierAwareBuildTreeLifecycleControllerFactory::class.java)
            registration.add(InjectedClasspathInstrumentationStrategy::class.java, VintageInjectedClasspathInstrumentationStrategy::class.java)
            registration.add(BuildTreeModelSideEffectExecutor::class.java, DefaultBuildTreeModelSideEffectExecutor::class.java)
            registration.add(ProjectScopedScriptResolution::class.java, ProjectScopedScriptResolution.NO_OP)
            registration.add(BuildTreeWorkGraphPreparer::class.java, DefaultBuildTreeWorkGraphPreparer::class.java)
            registration.add(ConfigurationCacheInputsListener::class.java, PromoInputsListener::class.java)
            registration.add(ExecutionAccessChecker::class.java, DefaultExecutionAccessChecker::class.java)
            // endregion

            // region VINTAGE
            registration.add(VintageConfigurationTimeActionRunner::class.java)
            // endregion
        }

        // region ALL MODES

        @Provides
        fun createLocalComponentCache(): LocalComponentCache = LocalComponentCache.NO_CACHE

        // endregion
    }

    /**
     * Build-scoped service implementations used for Vintage mode
     */
    @ServiceScope(Scope.Build::class)
    object Build : BaseGradleModeServices.Build() {

        override fun configure(registration: ServiceRegistration) {
            super.configure(registration)

            registration.add(BuildModelController::class.java, VintageBuildModelController::class.java)
            registration.add(CrossProjectModelAccess::class.java, DefaultCrossProjectModelAccess::class.java)
            registration.add(DynamicLookupRoutine::class.java, DefaultDynamicLookupRoutine::class.java)
        }

        @Provides
        fun createProjectEvaluator(lifecycleProjectEvaluator: LifecycleProjectEvaluator): ProjectEvaluator = lifecycleProjectEvaluator
    }

}

/**
 * Services implementations shared between CC and IP modes
 */
internal interface BaseConfigurationCacheGradleModeServices {

    /**
     * Build-tree-scoped service implementations shared between CC and IP modes
     */
    @ServiceScope(Scope.BuildTree::class)
    open class BuildTree : BaseGradleModeServices.BuildTree() {
        override fun configure(registration: ServiceRegistration) {
            super.configure(registration)

            // region ALL MODES service types
            registration.add(Environment::class.java, ConfigurationCacheEnvironment::class.java)
            registration.add(BuildTreeLifecycleControllerFactory::class.java, ConfigurationCacheBuildTreeLifecycleControllerFactory::class.java)
            registration.add(InjectedClasspathInstrumentationStrategy::class.java, ConfigurationCacheInjectedClasspathInstrumentationStrategy::class.java)

            registration.add(
                BuildTreeModelSideEffectExecutor::class.java,
                ConfigurationCacheBuildTreeModelSideEffectExecutor::class.java,
                ConfigurationCacheBuildTreeModelSideEffectExecutor::class.java
            )

            registration.add(
                ProjectScopedScriptResolution::class.java,
                ConfigurationCacheFingerprintController::class.java,
                ConfigurationCacheFingerprintController::class.java
            )

            registration.add(ConfigurationCacheInputsListener::class.java, InstrumentedInputAccessListener::class.java)
            // endregion

            // region CC and IP service types
            registration.add(ConfigurationCacheStartParameter::class.java)
            registration.add(ConfigurationCacheClassLoaderScopeRegistryListener::class.java)
            registration.add(BuildTreeConfigurationCache::class.java, DefaultConfigurationCache::class.java)
            registration.add(InstrumentedExecutionAccessListenerRegistry::class.java)
            registration.add(ConfigurationCacheInputFileChecker.Host::class.java, DefaultConfigurationCacheInputFileCheckerHost::class.java)
            registration.add(DefaultDeferredRootBuildGradle::class.java)
            // endregion
        }

        // region ALL MODES service types

        @Provides
        fun createBuildTreeWorkGraphPreparer(buildRegistry: BuildStateRegistry, buildTaskSelector: BuildTaskSelector, cache: BuildTreeConfigurationCache): BuildTreeWorkGraphPreparer {
            return ConfigurationCacheAwareBuildTreeWorkGraphPreparer(DefaultBuildTreeWorkGraphPreparer(buildRegistry, buildTaskSelector), cache)
        }

        @Provides
        fun createExecutionAccessChecker(
            listenerManager: ListenerManager,
            configurationTimeBarrier: ConfigurationTimeBarrier
        ): ExecutionAccessChecker {
            val broadcaster = listenerManager.getBroadcaster(ExecutionAccessListener::class.java)
            return ConfigurationTimeBarrierBasedExecutionAccessChecker(configurationTimeBarrier, broadcaster)
        }

        // endregion
    }

    /**
     * Build-scoped service implementations shared between CC and IP modes
     */
    @ServiceScope(Scope.Build::class)
    open class Build : BaseGradleModeServices.Build() {

        override fun configure(registration: ServiceRegistration) {
            super.configure(registration)
            registration.add(ProjectRefResolver::class.java)
        }

        @Provides
        fun createBuildModelController(
            gradle: GradleInternal,
            projectsPreparer: ProjectsPreparer,
            settingsPreparer: SettingsPreparer,
            taskExecutionPreparer: TaskExecutionPreparer,
            stateTransitionControllerFactory: StateTransitionControllerFactory,
            cache: BuildTreeConfigurationCache
        ): BuildModelController {

            val vintage = VintageBuildModelController(gradle, projectsPreparer, settingsPreparer, taskExecutionPreparer, stateTransitionControllerFactory)
            return ConfigurationCacheAwareBuildModelController(gradle, vintage, cache)
        }
    }

}

/**
 * Service implementations for CC mode
 */
internal interface ConfigurationCacheGradleModeServices {

    object BuildTree : BaseConfigurationCacheGradleModeServices.BuildTree() {
        override fun configure(registration: ServiceRegistration) {
            super.configure(registration)

            // region CC and IP service types
            registration.add(
                ClassLoaderScopesFingerprintController::class.java,
                ConfigurationCacheClassLoaderScopesFingerprintController::class.java
            )
            // endregion

        }

        // region ALL MODES service types

        @Provides
        fun createLocalComponentCache(): LocalComponentCache = LocalComponentCache.NO_CACHE

        // endregion
    }

    /**
     * Build-scoped service implementations for CC mode
     */
    @ServiceScope(Scope.Build::class)
    object Build : BaseConfigurationCacheGradleModeServices.Build() {

        override fun configure(registration: ServiceRegistration) {
            super.configure(registration)

            registration.add(CrossProjectModelAccess::class.java, DefaultCrossProjectModelAccess::class.java)
            registration.add(DynamicLookupRoutine::class.java, DefaultDynamicLookupRoutine::class.java)
        }

        @Provides
        fun createProjectEvaluator(lifecycleProjectEvaluator: LifecycleProjectEvaluator): ProjectEvaluator = lifecycleProjectEvaluator
    }

}

/**
 * Service implementations for IP mode
 */
internal interface IsolatedProjectsGradleModeServices {

    object BuildTree : BaseConfigurationCacheGradleModeServices.BuildTree() {
        override fun configure(registration: ServiceRegistration) {
            super.configure(registration)

            // region CC and IP service types
            registration.add(
                ClassLoaderScopesFingerprintController::class.java,
                IsolatedProjectsClassLoaderScopesFingerprintController::class.java
            )
            // endregion
        }

        // region ALL MODES service types

        @Provides
        fun createLocalComponentCache(parameters: BuildModelParameters, cache: BuildTreeConfigurationCache): LocalComponentCache {
            return when {
                parameters.isCachingModelBuilding -> ConfigurationCacheAwareLocalComponentCache(cache)
                else -> LocalComponentCache.NO_CACHE
            }
        }

        // endregion
    }

    /**
     * Build-scoped service implementations for IP mode
     */
    @ServiceScope(Scope.Build::class)
    object Build : BaseConfigurationCacheGradleModeServices.Build() {

        override fun configure(registration: ServiceRegistration) {
            super.configure(registration)
            registration.add(DynamicCallContextTracker::class.java, DefaultDynamicCallContextTracker::class.java)
            registration.add(DynamicLookupRoutine::class.java, TrackingDynamicLookupRoutine::class.java)
        }

        @Provides
        fun createCrossProjectModelAccess(
            projectRegistry: ProjectRegistry,
            problemsListener: ProblemsListener,
            problemFactory: ProblemFactory,
            coupledProjectsListener: CoupledProjectsListener,
            dynamicCallProblemReporting: DynamicCallProblemReporting,
            buildModelParameters: BuildModelParameters,
            instantiator: Instantiator,
            gradleLifecycleActionExecutor: GradleLifecycleActionExecutor
        ): CrossProjectModelAccess {
            val delegate = DefaultCrossProjectModelAccess(projectRegistry, instantiator, gradleLifecycleActionExecutor)
            return ProblemReportingCrossProjectModelAccess(delegate, problemsListener, coupledProjectsListener, problemFactory, dynamicCallProblemReporting, buildModelParameters, instantiator)
        }

        @Provides
        fun createDynamicCallProjectIsolationProblemReporting(dynamicCallContextTracker: DynamicCallContextTracker): DynamicCallProblemReporting =
            DefaultDynamicCallProblemReporting().also { reporting ->
                dynamicCallContextTracker.onEnter(reporting::enterDynamicCall)
                dynamicCallContextTracker.onLeave(reporting::leaveDynamicCall)
            }

        @Provides
        fun createProjectEvaluator(
            parameters: BuildModelParameters,
            lifecycleProjectEvaluator: LifecycleProjectEvaluator,
            fingerprintController: ConfigurationCacheFingerprintController,
        ): ProjectEvaluator {
            return when {
                parameters.isCachingModelBuilding -> ConfigurationCacheAwareProjectEvaluator(lifecycleProjectEvaluator, fingerprintController)
                else -> lifecycleProjectEvaluator
            }
        }
    }
}
