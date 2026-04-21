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

import org.gradle.api.internal.artifacts.ivyservice.ArtifactCachesProvider
import org.gradle.api.internal.artifacts.ivyservice.modulecache.FileStoreAndIndexProvider
import org.gradle.api.internal.artifacts.ivyservice.projectmodule.LocalComponentCache
import org.gradle.api.internal.file.temp.TemporaryFileProvider
import org.gradle.execution.ExecutionAccessChecker
import org.gradle.initialization.Environment
import org.gradle.internal.build.BuildToolingModelControllerFactory
import org.gradle.internal.buildoption.InternalOptions
import org.gradle.internal.buildtree.BuildActionModelRequirements
import org.gradle.internal.buildtree.BuildModelParameters
import org.gradle.internal.buildtree.BuildTreeLifecycleControllerFactory
import org.gradle.internal.buildtree.BuildTreeModelSideEffectExecutor
import org.gradle.internal.buildtree.BuildTreeWorkGraphPreparer
import org.gradle.internal.buildtree.DefaultBuildTreeModelSideEffectExecutor
import org.gradle.internal.buildtree.DefaultBuildTreeWorkGraphPreparer
import org.gradle.internal.cc.base.problems.IgnoringProblemsListener
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
import org.gradle.internal.cc.impl.problems.ConfigurationCacheProblems
import org.gradle.internal.cc.impl.promo.ConfigurationCachePromoHandler
import org.gradle.internal.cc.impl.promo.PromoInputsListener
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
import org.gradle.internal.nativeintegration.filesystem.FileSystem
import org.gradle.internal.resource.connector.ResourceConnectorFactory
import org.gradle.internal.resource.connector.ResourceConnectorSpecification
import org.gradle.internal.resource.transfer.ExternalResourceConnector
import org.gradle.internal.scripts.ProjectScopedScriptResolution
import org.gradle.internal.serialize.codecs.core.jos.JavaSerializationEncodingLookup
import org.gradle.internal.service.Provides
import org.gradle.internal.service.ServiceRegistration
import org.gradle.internal.service.ServiceRegistrationProvider
import org.gradle.plugin.use.resolve.service.internal.InjectedClasspathInstrumentationStrategy
import org.gradle.tooling.provider.model.internal.ToolingModelParameterCarrier
import java.io.File


internal
object BuildTreeModelControllerServices : ServiceRegistrationProvider {

    @Provides
    fun configure(
        registration: ServiceRegistration,
        modelParameters: BuildModelParameters,
        requirements: BuildActionModelRequirements,
    ): Unit = with(registration) {

        // region ALL MODES
        add(BuildNameProvider::class.java)
        add(ToolingModelParameterCarrier.Factory::class.java, DefaultToolingModelParameterCarrierFactory::class.java)
        add(BuildToolingModelControllerFactory::class.java, DefaultBuildToolingModelControllerFactory::class.java)
        add(JavaSerializationEncodingLookup::class.java)
        add(DeprecatedFeaturesListener::class.java)
        // This was originally only for the configuration cache, but now used for configuration cache and problems reporting
        add(ProblemFactory::class.java, DefaultProblemFactory::class.java)
        add(InputTrackingState::class.java)
        add(InstrumentedExecutionAccessListener::class.java)
        add(ConfigurationCacheProblemsListener::class.java, DefaultConfigurationCacheProblemsListener::class.java)
        // TODO: do these services have to be registered in all modes?
        add(DefaultConfigurationCacheDegradationController::class.java)
        add(ConfigurationCacheFingerprintEventHandler::class.java)
        // endregion

        // Set up CC problem reporting pipeline and promo, based on the build configuration
        when {
            // Collect and report problems. Don't suggest enabling CC if it is on, even if implicitly (e.g. enabled by isolated projects).
            // Most likely, the user who tries IP is already aware of CC and nudging will be just noise.
            modelParameters.isConfigurationCache -> add(ConfigurationCacheProblems::class.java)
            // Allow nudging to enable CC if it is off and there is no explicit decision. CC doesn't work for model building so do not nudge there.
            !requirements.startParameter.configurationCache.isExplicit && !requirements.isCreatesModel -> add(ConfigurationCachePromoHandler::class.java)
            // Do not nudge if CC is explicitly disabled or if models are requested.
            else -> add(ProblemsListener::class.java, IgnoringProblemsListener)
        }

        if (modelParameters.isVintage) {
            // region ALL MODES
            add(Environment::class.java, DefaultEnvironment::class.java)
            add(BuildTreeWorkGraphPreparer::class.java, DefaultBuildTreeWorkGraphPreparer::class.java)
            add(BuildTreeLifecycleControllerFactory::class.java, BarrierAwareBuildTreeLifecycleControllerFactory::class.java)
            add(InjectedClasspathInstrumentationStrategy::class.java, VintageInjectedClasspathInstrumentationStrategy::class.java)
            add(ProjectScopedScriptResolution::class.java, ProjectScopedScriptResolution.NO_OP)
            add(ConfigurationCacheInputsListener::class.java, PromoInputsListener::class.java)
            add(BuildTreeModelSideEffectExecutor::class.java, DefaultBuildTreeModelSideEffectExecutor::class.java)
            add(ExecutionAccessChecker::class.java, DefaultExecutionAccessChecker::class.java)
            // endregion

            // region VT-only
            add(VintageConfigurationTimeActionRunner::class.java)
            // endregion
        } else if (modelParameters.isConfigurationCache) {
            // region ALL MODES
            add(Environment::class.java, ConfigurationCacheEnvironment::class.java)
            add(BuildTreeWorkGraphPreparer::class.java, ConfigurationCacheAwareBuildTreeWorkGraphPreparer::class.java)
            add(BuildTreeLifecycleControllerFactory::class.java, ConfigurationCacheBuildTreeLifecycleControllerFactory::class.java)
            add(InjectedClasspathInstrumentationStrategy::class.java, ConfigurationCacheInjectedClasspathInstrumentationStrategy::class.java)
            add(ProjectScopedScriptResolution::class.java, ConfigurationCacheFingerprintController::class.java, ConfigurationCacheFingerprintController::class.java)
            add(ConfigurationCacheInputsListener::class.java, InstrumentedInputAccessListener::class.java)
            add(
                BuildTreeModelSideEffectExecutor::class.java,
                ConfigurationCacheBuildTreeModelSideEffectExecutor::class.java,
                ConfigurationCacheBuildTreeModelSideEffectExecutor::class.java
            )
            add(ExecutionAccessChecker::class.java, ConfigurationTimeBarrierBasedExecutionAccessChecker::class.java)
            // endregion

            // region CC and IP
            add(ConfigurationCacheStartParameter::class.java)
            add(ConfigurationCacheKey::class.java)
            add(ConfigurationCacheClassLoaderScopeRegistryListener::class.java)
            add(BuildTreeConfigurationCache::class.java, DefaultConfigurationCache::class.java)
            add(InstrumentedExecutionAccessListenerRegistry::class.java)
            add(DefaultDeferredRootBuildGradle::class.java)
            add(
                ConfigurationCacheInputFileChecker.Host::class.java,
                DefaultConfigurationCacheInputFileCheckerHost::class.java
            )

            if (modelParameters.isIsolatedProjects) {
                add(ClassLoaderScopesFingerprintController::class.java, IsolatedProjectsClassLoaderScopesFingerprintController::class.java)
            } else {
                add(ClassLoaderScopesFingerprintController::class.java, ConfigurationCacheClassLoaderScopesFingerprintController::class.java)
            }
            // endregion

        } else error("no other modes are supported")

        if (modelParameters.isCachingModelBuilding) {
            add(LocalComponentCache::class.java, ConfigurationCacheAwareLocalComponentCache::class.java)
        } else {
            add(LocalComponentCache::class.java, LocalComponentCache.NO_CACHE)
        }
    }

    // region ALL MODES

    @Provides
    fun createCommonReport(
        executorFactory: ExecutorFactory,
        temporaryFileProvider: TemporaryFileProvider,
        internalOptions: InternalOptions
    ): CommonReport {
        return CommonReport(executorFactory, temporaryFileProvider, internalOptions, "configuration cache report", "configuration-cache-report")
    }

    // endregion

    // region CC services, which are not instantiated in Vintage via laziness

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

    private
    fun hasIgnoredPaths(configurationCacheStartParameter: ConfigurationCacheStartParameter): Boolean =
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

    private
    fun httpResourceConnectorFrom(resourceConnectorFactories: List<ResourceConnectorFactory>): ExternalResourceConnector =
        resourceConnectorFactories
            .single { "https" in it.supportedProtocols }
            .createResourceConnector(object : ResourceConnectorSpecification {})

    // endregion
}
