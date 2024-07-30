/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.initialization.transform.registration;

import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.internal.initialization.DefaultScriptClassPathResolver;
import org.gradle.api.internal.initialization.ScriptClassPathResolutionContext;
import org.gradle.api.internal.initialization.transform.BaseInstrumentingArtifactTransform;
import org.gradle.api.internal.initialization.transform.ExternalDependencyInstrumentingArtifactTransform;
import org.gradle.api.internal.initialization.transform.InstrumentationAnalysisTransform;
import org.gradle.api.internal.initialization.transform.MergeInstrumentationAnalysisTransform;
import org.gradle.api.internal.initialization.transform.ProjectDependencyInstrumentingArtifactTransform;
import org.gradle.api.internal.initialization.transform.services.CacheInstrumentationDataBuildService;
import org.gradle.api.internal.provider.Providers;
import org.gradle.api.provider.Provider;
import org.gradle.api.services.BuildServiceRegistry;
import org.gradle.internal.id.IdGenerator;
import org.gradle.internal.id.LongIdGenerator;
import org.gradle.internal.instrumentation.agent.AgentStatus;
import org.gradle.internal.instrumentation.reporting.PropertyUpgradeReportConfig;
import org.gradle.internal.lazy.Lazy;

import java.util.function.Consumer;

import static org.gradle.api.internal.initialization.DefaultScriptClassPathResolver.INSTRUMENTED_ATTRIBUTE;
import static org.gradle.api.internal.initialization.DefaultScriptClassPathResolver.InstrumentationPhase.ANALYZED_ARTIFACT;
import static org.gradle.api.internal.initialization.DefaultScriptClassPathResolver.InstrumentationPhase.INSTRUMENTED_AND_UPGRADED;
import static org.gradle.api.internal.initialization.DefaultScriptClassPathResolver.InstrumentationPhase.INSTRUMENTED_ONLY;
import static org.gradle.api.internal.initialization.DefaultScriptClassPathResolver.InstrumentationPhase.MERGED_ARTIFACT_ANALYSIS;
import static org.gradle.api.internal.initialization.DefaultScriptClassPathResolver.InstrumentationPhase.NOT_INSTRUMENTED;

/**
 * Registers all Artifact transforms required for the instrumentation pipelines.
 */
public class InstrumentationTransformRegisterer {

    private static final String BUILD_SERVICE_NAME = "__InternalCacheInstrumentationDataBuildService__";

    private final AgentStatus agentStatus;
    private final Lazy<BuildServiceRegistry> buildServiceRegistry;
    private final IdGenerator<Long> contextIdGenerator;
    private final PropertyUpgradeReportConfig propertyUpgradeReportConfig;

    public InstrumentationTransformRegisterer(AgentStatus agentStatus, PropertyUpgradeReportConfig propertyUpgradeReportConfig, Lazy<BuildServiceRegistry> buildServiceRegistry) {
        this.buildServiceRegistry = buildServiceRegistry;
        this.contextIdGenerator = new LongIdGenerator();
        this.agentStatus = agentStatus;
        this.propertyUpgradeReportConfig = propertyUpgradeReportConfig;
    }

    public ScriptClassPathResolutionContext registerTransforms(DependencyHandler dependencyHandler) {
        long contextId = contextIdGenerator.generateId();
        Provider<CacheInstrumentationDataBuildService> service = buildServiceRegistry.get().registerIfAbsent(BUILD_SERVICE_NAME, CacheInstrumentationDataBuildService.class);
        registerInstrumentationAndUpgradesPipeline(contextId, dependencyHandler, service);
        registerInstrumentationOnlyPipeline(contextId, dependencyHandler);
        return new ScriptClassPathResolutionContext(contextId, service, dependencyHandler);
    }

    private void registerInstrumentationAndUpgradesPipeline(long contextId, DependencyHandler dependencyHandler, Provider<CacheInstrumentationDataBuildService> service) {
        dependencyHandler.registerTransform(
            InstrumentationAnalysisTransform.class,
            spec -> {
                spec.getFrom().attribute(INSTRUMENTED_ATTRIBUTE, NOT_INSTRUMENTED.getValue());
                spec.getTo().attribute(INSTRUMENTED_ATTRIBUTE, ANALYZED_ARTIFACT.getValue());
                spec.parameters(params -> {
                    params.getBuildService().set(service);
                    params.getContextId().set(contextId);
                });
            }
        );
        dependencyHandler.registerTransform(
            MergeInstrumentationAnalysisTransform.class,
            spec -> {
                spec.getFrom().attribute(INSTRUMENTED_ATTRIBUTE, ANALYZED_ARTIFACT.getValue());
                spec.getTo().attribute(INSTRUMENTED_ATTRIBUTE, MERGED_ARTIFACT_ANALYSIS.getValue());
                spec.parameters(params -> {
                    params.getBuildService().set(service);
                    params.getContextId().set(contextId);
                    params.getTypeHierarchyAnalysis().setFrom(service.map(it -> it.getTypeHierarchyAnalysis(contextId)));
                });
            }
        );
        registerInstrumentingTransform(
            contextId,
            dependencyHandler,
            ExternalDependencyInstrumentingArtifactTransform.class,
            service,
            MERGED_ARTIFACT_ANALYSIS,
            INSTRUMENTED_AND_UPGRADED,
            params -> {}
        );
    }

    private void registerInstrumentationOnlyPipeline(long contextId, DependencyHandler dependencyHandler) {
        registerInstrumentingTransform(contextId,
            dependencyHandler,
            ProjectDependencyInstrumentingArtifactTransform.class,
            Providers.notDefined(),
            NOT_INSTRUMENTED,
            INSTRUMENTED_ONLY,
            params -> params.getIsUpgradeReport().set(propertyUpgradeReportConfig.isEnabled())
        );
    }

    private <P extends BaseInstrumentingArtifactTransform.Parameters> void registerInstrumentingTransform(
        long contextId,
        DependencyHandler dependencyHandler,
        Class<? extends BaseInstrumentingArtifactTransform<P>> transform,
        Provider<CacheInstrumentationDataBuildService> service,
        DefaultScriptClassPathResolver.InstrumentationPhase fromPhase,
        DefaultScriptClassPathResolver.InstrumentationPhase toPhase,
        Consumer<P> paramsConfiguration
    ) {
        dependencyHandler.registerTransform(
            transform,
            spec -> {
                spec.getFrom().attribute(INSTRUMENTED_ATTRIBUTE, fromPhase.getValue());
                spec.getTo().attribute(INSTRUMENTED_ATTRIBUTE, toPhase.getValue());
                spec.parameters(params -> {
                    params.getBuildService().set(service);
                    params.getContextId().set(contextId);
                    params.getAgentSupported().set(agentStatus.isAgentInstrumentationEnabled());
                    paramsConfiguration.accept(params);
                });
            }
        );
    }
}
