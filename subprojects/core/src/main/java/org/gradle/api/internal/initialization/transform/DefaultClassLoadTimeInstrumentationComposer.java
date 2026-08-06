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

package org.gradle.api.internal.initialization.transform;

import com.google.common.collect.ImmutableMap;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.initialization.DefaultScriptClassPathResolver;
import org.gradle.api.internal.initialization.transform.utils.CachedInstrumentationAnalysisSerializer;
import org.gradle.api.internal.initialization.transform.utils.DefaultInstrumentationAnalysisSerializer;
import org.gradle.api.internal.initialization.transform.utils.InstrumentationAnalysisSerializer;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.TransformedClassPath;
import org.gradle.internal.classpath.TransformedClassPath.TransformedEntry;
import org.gradle.internal.classpath.transforms.ClassTransform;
import org.gradle.internal.classpath.transforms.InstrumentingClassLoadTimeTransform;
import org.gradle.internal.classpath.transforms.InstrumentingClassTransform;
import org.gradle.internal.classpath.types.InstrumentationTypeRegistry;
import org.gradle.internal.classpath.types.PropertiesBackedInstrumentationTypeRegistry;
import org.gradle.internal.instrumentation.agent.AgentStatus;
import org.gradle.internal.instrumentation.agent.ThirdPartyAgentDetection;
import org.gradle.internal.instrumentation.api.types.BytecodeInterceptorFilter;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.Map;

/**
 * Composes Gradle instrumentation with a third-party agent by attaching a class-load-time transform
 * to a {@link TransformedClassPath}. Classloaders built from such a classpath load the original
 * (non-substituted) entries, let the third-party agent transform their classes and re-apply
 * Gradle instrumentation on top of the result.
 * <p>
 * The transform is derived data: it is reconstructed from the instrumentation metadata carried
 * by the classpath entries and the JVM-constant agent status. Every producer of a
 * {@link TransformedClassPath} that reaches a classloader has to compose it explicitly:
 * currently {@link DefaultScriptClassPathResolver} after resolution, and the configuration cache
 * class decoder after a cache hit ({@code DefaultClassDecoder.scopeFor}).
 */
@NullMarked
@ServiceScope(Scope.BuildSession.class)
public class DefaultClassLoadTimeInstrumentationComposer implements ClassLoadTimeInstrumentationComposer {
    // The transform is stateless, and so shared by all project entries of all composed classpaths.
    private static final ClassTransform PROJECT_DEPENDENCY_TRANSFORM = new InstrumentingClassTransform(BytecodeInterceptorFilter.INSTRUMENTATION_ONLY, InstrumentationTypeRegistry.EMPTY);

    private final AgentStatus agentStatus;
    private final InstrumentationAnalysisSerializer analysisSerializer;

    public DefaultClassLoadTimeInstrumentationComposer(AgentStatus agentStatus, StringInterner stringInterner) {
        this.agentStatus = agentStatus;
        // The same analysis file is typically referenced by many classpaths (one per scope sharing the jar),
        // so cache the deserialized data instead of re-reading it per composed classloader.
        this.analysisSerializer = new CachedInstrumentationAnalysisSerializer(new DefaultInstrumentationAnalysisSerializer(stringInterner));
    }

    @Override
    public ClassPath composeWithThirdPartyAgentIfPresent(ClassPath classPath) {
        if (!(classPath instanceof TransformedClassPath) || !shouldComposeAtClassLoadTime()) {
            return classPath;
        }
        TransformedClassPath transformedClassPath = (TransformedClassPath) classPath;
        Map<File, ClassTransform> transformsByOrigin = transformsByOrigin(transformedClassPath);
        if (transformsByOrigin.isEmpty()) {
            return classPath;
        }
        return transformedClassPath.withClassLoadTimeTransform(new InstrumentingClassLoadTimeTransform(transformsByOrigin));
    }

    private boolean shouldComposeAtClassLoadTime() {
        return agentStatus.isAgentInstrumentationEnabled() && ThirdPartyAgentDetection.isThirdPartyAgentPresent();
    }

    private Map<File, ClassTransform> transformsByOrigin(TransformedClassPath classPath) {
        ImmutableMap.Builder<File, ClassTransform> builder = ImmutableMap.builder();
        for (File original : classPath.getAsFiles()) {
            TransformedEntry entry = classPath.findEntryFor(original);
            ClassTransform transform = entry != null ? transformFor(entry) : null;
            if (transform != null) {
                builder.put(original, transform);
            }
        }
        return builder.build();
    }

    @Nullable
    private ClassTransform transformFor(TransformedEntry entry) {
        // TODO(mlopatkin): We don't apply property upgrade report transformation, is the report deprecated?
        switch (entry.getKind()) {
            case EXTERNAL_DEPENDENCY:
                // Matches ExternalDependencyInstrumentingArtifactTransform: upgrades use the type hierarchy
                // recorded in the per-artifact dependency analysis file.
                return new InstrumentingClassTransform(BytecodeInterceptorFilter.INSTRUMENTATION_AND_BYTECODE_UPGRADE, typeRegistryOf(entry));
            case PROJECT_DEPENDENCY:
                // Matches ProjectDependencyInstrumentingArtifactTransform, without the property-upgrade reporting.
                return PROJECT_DEPENDENCY_TRANSFORM;
            case UNKNOWN:
                // The producing pipeline is not recorded, so there is no way to tell how to re-instrument the entry.
                return null;
            default:
                throw new AssertionError("Unexpected instrumentation kind: " + entry.getKind());
        }
    }

    private InstrumentationTypeRegistry typeRegistryOf(TransformedEntry entry) {
        File analysisFile = entry.getAnalysisFile();
        if (analysisFile == null) {
            return InstrumentationTypeRegistry.EMPTY;
        }
        return PropertiesBackedInstrumentationTypeRegistry.of(() -> {
            // The registry is only consulted at class load, which may happen long after composition:
            // by then the analysis file, which lives in the transforms cache, may have fallen victim
            // to cache cleanup while the daemon retained the transform result in memory.
            // Fail with a clear message rather than an opaque class loading error.
            if (!analysisFile.isFile()) {
                throw new IllegalStateException(
                    "The dependency analysis file " + analysisFile + " needed to instrument the buildscript classpath is missing, "
                        + "most likely because it was removed by Gradle cache cleanup. Stop the Gradle daemons and run the build again to regenerate it.");
            }
            return analysisSerializer.readDependencyAnalysis(analysisFile).getDependencies();
        });
    }
}
