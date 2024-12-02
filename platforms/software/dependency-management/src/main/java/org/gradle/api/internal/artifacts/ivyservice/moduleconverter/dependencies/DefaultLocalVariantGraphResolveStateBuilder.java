/*
 * Copyright 2007-2009 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.Named;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencyConstraint;
import org.gradle.api.artifacts.ExcludeRule;
import org.gradle.api.artifacts.FileCollectionDependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.attributes.Category;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.configurations.Configurations;
import org.gradle.api.internal.artifacts.configurations.ConfigurationsProvider;
import org.gradle.api.internal.artifacts.dependencies.SelfResolvingDependencyInternal;
import org.gradle.api.internal.attributes.AttributeValue;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.internal.Describables;
import org.gradle.internal.DisplayName;
import org.gradle.internal.component.external.model.ImmutableCapabilities;
import org.gradle.internal.component.local.model.DefaultLocalVariantGraphResolveMetadata;
import org.gradle.internal.component.local.model.DefaultLocalVariantGraphResolveState;
import org.gradle.internal.component.local.model.LocalComponentArtifactMetadata;
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata;
import org.gradle.internal.component.local.model.LocalVariantGraphResolveMetadata;
import org.gradle.internal.component.local.model.LocalVariantGraphResolveState;
import org.gradle.internal.component.local.model.LocalVariantMetadata;
import org.gradle.internal.component.local.model.PublishArtifactLocalArtifactMetadata;
import org.gradle.internal.component.model.ComponentConfigurationIdentifier;
import org.gradle.internal.component.model.ComponentIdGenerator;
import org.gradle.internal.component.model.ExcludeMetadata;
import org.gradle.internal.component.model.LocalOriginDependencyMetadata;
import org.gradle.internal.component.model.VariantResolveMetadata;
import org.gradle.internal.model.CalculatedValue;
import org.gradle.internal.model.CalculatedValueContainerFactory;
import org.gradle.internal.model.ModelContainer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

/**
 * Encapsulates all logic required to build a {@link LocalVariantGraphResolveMetadata} from a
 * {@link ConfigurationInternal}. Utilizes caching to prevent unnecessary duplicate conversions
 * between DSL and internal metadata types.
 */
public class DefaultLocalVariantGraphResolveStateBuilder implements LocalVariantGraphResolveStateBuilder {

    private final ComponentIdGenerator idGenerator;
    private final DependencyMetadataFactory dependencyMetadataFactory;
    private final ExcludeRuleConverter excludeRuleConverter;

    public DefaultLocalVariantGraphResolveStateBuilder(
        ComponentIdGenerator idGenerator,
        DependencyMetadataFactory dependencyMetadataFactory,
        ExcludeRuleConverter excludeRuleConverter
    ) {
        this.idGenerator = idGenerator;
        this.dependencyMetadataFactory = dependencyMetadataFactory;
        this.excludeRuleConverter = excludeRuleConverter;
    }

    @Override
    public LocalVariantGraphResolveState create(
        ConfigurationInternal configuration,
        ConfigurationsProvider configurationsProvider,
        ComponentIdentifier componentId,
        DependencyCache dependencyCache,
        ModelContainer<?> model,
        CalculatedValueContainerFactory calculatedValueContainerFactory
    ) {
        // Perform any final mutating actions for this configuration and its parents.
        // Then, lock this configuration and its parents from mutation.
        // After we observe a configuration (by building its metadata), its state should not change.
        configuration.runDependencyActions();
        configuration.markAsObserved();

        String configurationName = configuration.getName();
        DisplayName description = configuration.asDescribable();
        ComponentConfigurationIdentifier configurationIdentifier = new ComponentConfigurationIdentifier(componentId, configurationName);

        ImmutableAttributes attributes = configuration.getAttributes().asImmutable();
        ImmutableCapabilities capabilities = ImmutableCapabilities.of(Configurations.collectCapabilities(configuration, new HashSet<>(), new HashSet<>()));

        // Collect all artifact sets.
        ImmutableSet.Builder<LocalVariantMetadata> artifactSets = ImmutableSet.builder();
//        DeprecationLogger.whileDisabled(() -> {
            // Ignore deprecations when collecting artifacts from the configuration.
            // If this configuration is deprecated for consumption, we will emit a warning later on when it is consumed.
            configuration.collectVariants(new ConfigurationInternal.VariantVisitor() {
                @Override
                public void visitOwnVariant(DisplayName displayName, ImmutableAttributes attributes, Collection<? extends PublishArtifact> artifacts) {
                    CalculatedValue<ImmutableList<LocalComponentArtifactMetadata>> variantArtifacts = getVariantArtifacts(displayName, componentId, artifacts, model, calculatedValueContainerFactory);
                    artifactSets.add(new LocalVariantMetadata(configurationName, configurationIdentifier, displayName, attributes, capabilities, variantArtifacts));
                }

                @Override
                public void visitChildVariant(String name, DisplayName displayName, ImmutableAttributes attributes, Collection<? extends PublishArtifact> artifacts) {
                    CalculatedValue<ImmutableList<LocalComponentArtifactMetadata>> variantArtifacts = getVariantArtifacts(displayName, componentId, artifacts, model, calculatedValueContainerFactory);
                    artifactSets.add(new LocalVariantMetadata(configurationName + "-" + name, new NonImplicitArtifactVariantIdentifier(configurationIdentifier, name), displayName, attributes, capabilities, variantArtifacts));
                }
            });
//        });

        // Collect all dependencies and excludes in hierarchy.
        // After running the dependency actions and preventing from mutation above, we know the
        // hierarchy will not change anymore and all configurations in the hierarchy
        // will no longer be mutated.
        Set<Configuration> hierarchy = configuration.getHierarchy();
        CalculatedValue<DefaultLocalVariantGraphResolveState.VariantDependencyMetadata> dependencies =
            getConfigurationDependencyState(description, hierarchy, attributes, dependencyCache, model, calculatedValueContainerFactory);

        LocalVariantGraphResolveMetadata metadata = new DefaultLocalVariantGraphResolveMetadata(
            configurationName,
            configuration.isTransitive(),
            attributes,
            capabilities,
            configuration.isDeprecatedForConsumption()
        );

        return new DefaultLocalVariantGraphResolveState(
            idGenerator.nextVariantId(),
            componentId,
            metadata,
            idGenerator,
            calculatedValueContainerFactory,
            dependencies,
            artifactSets.build()
        );
    }

    private static CalculatedValue<ImmutableList<LocalComponentArtifactMetadata>> getVariantArtifacts(
        DisplayName displayName,
        ComponentIdentifier componentId,
        Collection<? extends PublishArtifact> sourceArtifacts,
        ModelContainer<?> model,
        CalculatedValueContainerFactory calculatedValueContainerFactory
    ) {
        return calculatedValueContainerFactory.create(Describables.of(displayName, "artifacts"), context -> {
            if (sourceArtifacts.isEmpty()) {
                return ImmutableList.of();
            } else {
                return model.fromMutableState(m -> {
                    ImmutableList.Builder<LocalComponentArtifactMetadata> result = ImmutableList.builderWithExpectedSize(sourceArtifacts.size());
                    for (PublishArtifact sourceArtifact : sourceArtifacts) {
                        result.add(new PublishArtifactLocalArtifactMetadata(componentId, sourceArtifact));
                    }
                    return result.build();
                });
            }
        });
    }

    /**
     * Lazily collect all dependencies and excludes of all configurations in the provided {@code hierarchy}.
     */
    private CalculatedValue<DefaultLocalVariantGraphResolveState.VariantDependencyMetadata> getConfigurationDependencyState(
        DisplayName description,
        Set<Configuration> hierarchy,
        ImmutableAttributes attributes,
        DependencyCache dependencyCache,
        ModelContainer<?> model,
        CalculatedValueContainerFactory calculatedValueContainerFactory
    ) {
        return calculatedValueContainerFactory.create(Describables.of("Dependency state for", description), context -> model.fromMutableState(p -> {
            ImmutableList.Builder<LocalOriginDependencyMetadata> dependencies = ImmutableList.builder();
            ImmutableSet.Builder<LocalFileDependencyMetadata> files = ImmutableSet.builder();
            ImmutableList.Builder<ExcludeMetadata> excludes = ImmutableList.builder();

            // For historical reasons, and to maintain behavior, dependencies
            // are ordered based on the name of the extended configurations.
            ArrayList<Configuration> sortedHierarchy = new ArrayList<>(hierarchy);
            sortedHierarchy.sort(Comparator.comparing(Named::getName));
            sortedHierarchy.forEach(config -> {
                DependencyState defined = getDefinedState((ConfigurationInternal) config, dependencyCache);
                dependencies.addAll(defined.dependencies);
                files.addAll(defined.files);
                excludes.addAll(defined.excludes);
            });

            DependencyState state = new DependencyState(dependencies.build(), files.build(), excludes.build());
            return new DefaultLocalVariantGraphResolveState.VariantDependencyMetadata(
                maybeForceDependencies(state.dependencies, attributes), state.files, state.excludes
            );
        }));
    }

    /**
     * Get the defined dependencies and excludes for {@code configuration}, while also caching the result.
     */
    private DependencyState getDefinedState(ConfigurationInternal configuration, DependencyCache cache) {
        return cache.computeIfAbsent(configuration, this::doGetDefinedState);
    }

    /**
     * Calculate the defined dependencies and excludes for {@code configuration}, while converting the
     * DSL representation to the internal representation.
     */
    private DependencyState doGetDefinedState(ConfigurationInternal configuration) {

        ImmutableList.Builder<LocalOriginDependencyMetadata> dependencyBuilder = ImmutableList.builder();
        ImmutableSet.Builder<LocalFileDependencyMetadata> fileBuilder = ImmutableSet.builder();
        ImmutableList.Builder<ExcludeMetadata> excludeBuilder = ImmutableList.builder();

        // Configurations that are not declarable should not have dependencies or constraints present,
        // but we need to allow dependencies to be checked to avoid emitting many warnings when the
        // Kotlin plugin is applied.  This is because applying the Kotlin plugin adds dependencies
        // to the testRuntimeClasspath configuration, which is not declarable.
        // To demonstrate this, add a check for configuration.isCanBeDeclared() && configuration.assertHasNoDeclarations() if not
        // and run tests such as KotlinDslPluginTest, or the building-kotlin-applications samples and you'll configurations which
        // aren't declarable but have declared dependencies present.
        for (Dependency dependency : configuration.getDependencies()) {
            if (dependency instanceof ModuleDependency) {
                ModuleDependency moduleDependency = (ModuleDependency) dependency;
                dependencyBuilder.add(dependencyMetadataFactory.createDependencyMetadata(moduleDependency));
            } else if (dependency instanceof FileCollectionDependency) {
                final FileCollectionDependency fileDependency = (FileCollectionDependency) dependency;
                fileBuilder.add(new DefaultLocalFileDependencyMetadata(fileDependency));
            } else {
                throw new IllegalArgumentException("Cannot convert dependency " + dependency + " to local component dependency metadata.");
            }
        }

        // Configurations that are not declarable should not have dependencies or constraints present,
        // no smoke-tested plugins add constraints, so we should be able to safely throw an exception here
        // if we find any - but we'll avoid doing so for now to avoid breaking any existing builds and to
        // remain consistent with the behavior for dependencies.
        for (DependencyConstraint dependencyConstraint : configuration.getDependencyConstraints()) {
            dependencyBuilder.add(dependencyMetadataFactory.createDependencyConstraintMetadata(dependencyConstraint));
        }

        for (ExcludeRule excludeRule : configuration.getExcludeRules()) {
            excludeBuilder.add(excludeRuleConverter.convertExcludeRule(excludeRule));
        }

        return new DependencyState(dependencyBuilder.build(), fileBuilder.build(), excludeBuilder.build());
    }

    private static ImmutableList<LocalOriginDependencyMetadata> maybeForceDependencies(
        ImmutableList<LocalOriginDependencyMetadata> dependencies,
        ImmutableAttributes attributes
    ) {
        AttributeValue<Category> attributeValue = attributes.findEntry(Category.CATEGORY_ATTRIBUTE);
        if (!attributeValue.isPresent() || !attributeValue.get().getName().equals(Category.ENFORCED_PLATFORM)) {
            return dependencies;
        }

        // Need to wrap all dependencies to force them.
        ImmutableList.Builder<LocalOriginDependencyMetadata> forcedDependencies = ImmutableList.builder();
        for (LocalOriginDependencyMetadata rawDependency : dependencies) {
            forcedDependencies.add(rawDependency.forced());
        }
        return forcedDependencies.build();
    }

    /**
     * Identifier for non-implicit artifact variants of a local graph variant.
     */
    private static class NonImplicitArtifactVariantIdentifier implements VariantResolveMetadata.Identifier {
        private final VariantResolveMetadata.Identifier parent;
        private final String name;

        private final int hashCode;

        public NonImplicitArtifactVariantIdentifier(VariantResolveMetadata.Identifier parent, String name) {
            this.parent = parent;
            this.name = name;

            this.hashCode = computeHashCode(name, parent);
        }

        private static int computeHashCode(String name, VariantResolveMetadata.Identifier parent) {
            return 31 * parent.hashCode() + name.hashCode();
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            NonImplicitArtifactVariantIdentifier other = (NonImplicitArtifactVariantIdentifier) obj;
            return parent.equals(other.parent) && name.equals(other.name);
        }
    }

    /**
     * Default implementation of {@link LocalFileDependencyMetadata}.
     */
    private static class DefaultLocalFileDependencyMetadata implements LocalFileDependencyMetadata {
        private final FileCollectionDependency fileDependency;

        DefaultLocalFileDependencyMetadata(FileCollectionDependency fileDependency) {
            this.fileDependency = fileDependency;
        }

        @Override
        public FileCollectionDependency getSource() {
            return fileDependency;
        }

        @Override @Nullable
        public ComponentIdentifier getComponentId() {
            return ((SelfResolvingDependencyInternal) fileDependency).getTargetComponentId();
        }

        @Override
        public FileCollectionInternal getFiles() {
            return (FileCollectionInternal) fileDependency.getFiles();
        }
    }
}
