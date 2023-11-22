/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.api.internal.initialization;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.Action;
import org.gradle.api.JavaVersion;
import org.gradle.api.artifacts.ArtifactView;
import org.gradle.api.artifacts.ComponentMetadataContext;
import org.gradle.api.artifacts.ComponentMetadataRule;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeCompatibilityRule;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.AttributeDisambiguationRule;
import org.gradle.api.attributes.AttributeMatchingStrategy;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.CompatibilityCheckDetails;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.MultipleCandidatesDetails;
import org.gradle.api.attributes.Usage;
import org.gradle.api.attributes.java.TargetJvmVersion;
import org.gradle.api.attributes.plugin.GradlePluginApiVersion;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactoryInternal;
import org.gradle.api.internal.initialization.transform.BuildScriptInstrumentingArtifactTransform;
import org.gradle.api.internal.initialization.transform.CollectDirectClassSuperTypesTransform;
import org.gradle.api.internal.initialization.transform.InstrumentingArtifactTransform;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.cache.GlobalCache;
import org.gradle.internal.agents.AgentStatus;
import org.gradle.internal.classanalysis.AsmConstants;
import org.gradle.internal.classpath.CachedClasspathTransformer;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.classpath.TransformedClassPath;
import org.gradle.internal.classpath.types.GradleCoreInstrumentingTypeRegistry;
import org.gradle.internal.component.local.model.OpaqueComponentIdentifier;
import org.gradle.internal.logging.util.Log4jBannedVersion;
import org.gradle.util.GradleVersion;

import java.io.File;
import java.io.Serializable;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class DefaultScriptClassPathResolver implements ScriptClassPathResolver {

    private static final Set<DependencyFactoryInternal.ClassPathNotation> NO_GRADLE_API = EnumSet.copyOf(ImmutableSet.of(
        DependencyFactoryInternal.ClassPathNotation.GRADLE_API,
        DependencyFactoryInternal.ClassPathNotation.LOCAL_GROOVY
    ));

    public static final Attribute<Boolean> HIERARCHY_COLLECTED_ATTRIBUTE = Attribute.of("org.gradle.internal.hierarchy-collected", Boolean.class);
    public static final Attribute<Boolean> INSTRUMENTED_ATTRIBUTE = Attribute.of("org.gradle.internal.instrumented", Boolean.class);
    public static final Attribute<Boolean> BUILD_SCRIPT_INSTRUMENTED_ATTRIBUTE = Attribute.of("org.gradle.internal.buildscript.instrumented", Boolean.class);
    private final NamedObjectInstantiator instantiator;
    private final CachedClasspathTransformer classpathTransformer;
    private final List<GlobalCache> globalCaches;
    private final AgentStatus agentStatus;
    private final GradleCoreInstrumentingTypeRegistry gradleCoreInstrumentingTypeRegistry;

    public DefaultScriptClassPathResolver(
        NamedObjectInstantiator instantiator,
        CachedClasspathTransformer classpathTransformer,
        List<GlobalCache> globalCaches,
        AgentStatus agentStatus,
        GradleCoreInstrumentingTypeRegistry gradleCoreInstrumentingTypeRegistry
    ) {
        this.instantiator = instantiator;
        this.classpathTransformer = classpathTransformer;
        this.globalCaches = globalCaches;
        this.agentStatus = agentStatus;
        this.gradleCoreInstrumentingTypeRegistry = gradleCoreInstrumentingTypeRegistry;
    }

    @Override
    public void prepareClassPath(Configuration configuration, DependencyHandler dependencyHandler) {
        // should ideally reuse the `JvmPluginServices` but this code is too low level
        // and this service is therefore not available!
        AttributeContainer attributes = configuration.getAttributes();
        attributes.attribute(Usage.USAGE_ATTRIBUTE, instantiator.named(Usage.class, Usage.JAVA_RUNTIME));
        attributes.attribute(Category.CATEGORY_ATTRIBUTE, instantiator.named(Category.class, Category.LIBRARY));
        attributes.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, instantiator.named(LibraryElements.class, LibraryElements.JAR));
        attributes.attribute(Bundling.BUNDLING_ATTRIBUTE, instantiator.named(Bundling.class, Bundling.EXTERNAL));
        attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, Integer.parseInt(JavaVersion.current().getMajorVersion()));
        attributes.attribute(GradlePluginApiVersion.GRADLE_PLUGIN_API_VERSION_ATTRIBUTE, instantiator.named(GradlePluginApiVersion.class, GradleVersion.current().getVersion()));

        configuration.getDependencyConstraints().add(dependencyHandler.getConstraints().create(Log4jBannedVersion.LOG4J2_CORE_COORDINATES, constraint -> constraint.version(version -> {
            version.require(Log4jBannedVersion.LOG4J2_CORE_REQUIRED_VERSION);
            version.reject(Log4jBannedVersion.LOG4J2_CORE_VULNERABLE_VERSION_RANGE);
        })));

        // Register instrumentation transform for build scripts
        dependencyHandler.registerTransform(
            BuildScriptInstrumentingArtifactTransform.class,
            spec -> {
                spec.getFrom().attribute(BUILD_SCRIPT_INSTRUMENTED_ATTRIBUTE, false);
                spec.getTo().attribute(BUILD_SCRIPT_INSTRUMENTED_ATTRIBUTE, true);
                spec.parameters(parameters -> parameters.getMaxSupportedJavaVersion().set(AsmConstants.MAX_SUPPORTED_JAVA_VERSION));
            }
        );

//        dependencyHandler.attributesSchema(attributesSchema -> {
//            AttributeMatchingStrategy<Boolean> attribute = attributesSchema.attribute(BUILD_SCRIPT_INSTRUMENTED_ATTRIBUTE);
//            attribute.getDisambiguationRules()
//                .add(BuildScriptInstrumentationDisambiguationRule.class);
//            attribute.getCompatibilityRules()
//                .add(BuildScriptInstrumentationCompatibilityRule.class);
//            }
//        );

//        dependencyHandler.components(componentMetadataHandler -> componentMetadataHandler.all(BuildScriptVariantDerivationRule.class));
        dependencyHandler.getArtifactTypes().getByName("jar").getAttributes()
            .attribute(INSTRUMENTED_ATTRIBUTE, false)
            .attribute(HIERARCHY_COLLECTED_ATTRIBUTE, false)
            .attribute(BUILD_SCRIPT_INSTRUMENTED_ATTRIBUTE, false);

        dependencyHandler.getArtifactTypes().getByName("directory").getAttributes()
            .attribute(BUILD_SCRIPT_INSTRUMENTED_ATTRIBUTE, false);
    }

    @Override
    public ClassPath resolveClassPath(Configuration classpathConfiguration, DependencyHandler dependencyHandler, ConfigurationContainer configContainer) {
        FileCollection instrumentedView = getInstrumentedView(classpathConfiguration, dependencyHandler, configContainer);
        return TransformedClassPath.handleInstrumentingArtifactTransform(DefaultClassPath.of(instrumentedView));
    }

    private FileCollection getInstrumentedView(Configuration classpathConfiguration, DependencyHandler dependencyHandler, ConfigurationContainer configContainer) {
        // Register collect type hierarchy
        ArtifactView hierarchyCollectedView = artifactView(classpathConfiguration, config -> {
            config.attributes(it -> it.attribute(HIERARCHY_COLLECTED_ATTRIBUTE, true));
            config.componentFilter(id -> !(id instanceof ProjectComponentIdentifier) && DefaultScriptClassPathResolver.filterGradleDependencies(id));
        });
        dependencyHandler.registerTransform(
            CollectDirectClassSuperTypesTransform.class,
            spec -> {
                spec.getFrom().attribute(HIERARCHY_COLLECTED_ATTRIBUTE, false);
                spec.getTo().attribute(HIERARCHY_COLLECTED_ATTRIBUTE, true);
            }
        );

        // Register instrumentation and upgrades transform
        dependencyHandler.registerTransform(
            InstrumentingArtifactTransform.class,
            spec -> {
                spec.getFrom().attribute(INSTRUMENTED_ATTRIBUTE, false);
                spec.getTo().attribute(INSTRUMENTED_ATTRIBUTE, true);
                spec.parameters(parameters -> {
                    parameters.getClassHierarchy().setFrom(hierarchyCollectedView.getFiles());
                    parameters.getCacheLocations().set(getSerializableGlobalCaches());
                    parameters.getAgentSupported().set(agentStatus.isAgentInstrumentationEnabled());
                    parameters.getMaxSupportedJavaVersion().set(AsmConstants.MAX_SUPPORTED_JAVA_VERSION);
                    parameters.getUpgradedPropertiesHash().set(gradleCoreInstrumentingTypeRegistry.getUpgradedPropertiesHash().map(Object::toString).orElse(null));
                });
            }
        );

        return artifactView(classpathConfiguration, config -> {
            config.attributes(it -> it.attribute(INSTRUMENTED_ATTRIBUTE, true));
            config.componentFilter(DefaultScriptClassPathResolver::filterGradleDependencies);
        }).getFiles();
    }

    private List<GlobalCache> getSerializableGlobalCaches() {
        return globalCaches.stream()
            .map(SerializableGlobalCache::new)
            .collect(Collectors.toList());
    }

    private static ArtifactView artifactView(Configuration configuration, Action<? super ArtifactView.ViewConfiguration> configAction) {
        return configuration.getIncoming().artifactView(configAction);
    }

    private static boolean filterGradleDependencies(ComponentIdentifier componentId) {
        if (componentId instanceof OpaqueComponentIdentifier) {
            DependencyFactoryInternal.ClassPathNotation classPathNotation = ((OpaqueComponentIdentifier) componentId).getClassPathNotation();
            return !DefaultScriptClassPathResolver.NO_GRADLE_API.contains(classPathNotation);
        }
        return true;
    }

    private static class SerializableGlobalCache implements GlobalCache, Serializable {
        private static final long serialVersionUID = 1L;

        private final List<File> cacheRoots;

        public SerializableGlobalCache(GlobalCache other) {
            this.cacheRoots = other.getGlobalCacheRoots();
        }

        @Override
        public List<File> getGlobalCacheRoots() {
            return cacheRoots;
        }
    }

    public static class BuildScriptInstrumentationDisambiguationRule implements AttributeDisambiguationRule<Boolean> {
        @Override
        public void execute(MultipleCandidatesDetails<Boolean> details) {
            if (details.getConsumerValue() == null) {
                details.closestMatch(false);
            }
        }
    }

    public static class BuildScriptInstrumentationCompatibilityRule implements AttributeCompatibilityRule<Boolean> {
        @Override
        public void execute(CompatibilityCheckDetails<Boolean> details) {
            Boolean consumer = details.getConsumerValue();
            Boolean producer = details.getProducerValue();
            if (consumer == null && producer == null || consumer == false && producer == false) {
                details.compatible();
            } else {
                details.incompatible();
            }
        }
    }

    public static class BuildScriptVariantDerivationRule implements ComponentMetadataRule {
        @Override
        public void execute(ComponentMetadataContext context) {
            context.getDetails().addVariant("default", variant -> variant.attributes(attributes -> attributes.attribute(BUILD_SCRIPT_INSTRUMENTED_ATTRIBUTE, false)));
        }
    }
}
