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
package org.gradle.internal.component.external.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.api.artifacts.DependencyConstraintMetadata;
import org.gradle.api.artifacts.DependencyConstraintsMetadata;
import org.gradle.api.artifacts.DirectDependenciesMetadata;
import org.gradle.api.artifacts.DirectDependencyMetadata;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.MutableVariantFilesMetadata;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.capabilities.MutableCapabilitiesMetadata;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.internal.component.model.CapabilitiesRules;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.DependencyMetadataRules;
import org.gradle.internal.component.model.VariantAttributesRules;
import org.gradle.internal.component.model.VariantFilesRules;
import org.gradle.internal.component.model.VariantResolveMetadata;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.NotationParser;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class VariantMetadataRules {
    private final ImmutableAttributesFactory attributesFactory;
    private final ModuleVersionIdentifier moduleVersionId;
    private final List<AdditionalVariant> additionalVariants = Lists.newArrayList();

    private DependencyMetadataRules dependencyMetadataRules;
    private VariantAttributesRules variantAttributesRules;
    private CapabilitiesRules capabilitiesRules;
    private VariantFilesRules variantFilesRules;

    private final AttributeContainerInternal baseAttributes;
    // If two configurations have a dependency on the same module, there is a chance they can be
    // resolved concurrently. Dependency resolution exercises this code when performing attribute
    // matching, so this map must support concurrent modification.
    private final Map<String, AttributeContainerInternal> variantAttributes = new ConcurrentHashMap<>();

    public VariantMetadataRules(ImmutableAttributesFactory attributesFactory, ModuleVersionIdentifier moduleVersionId) {
        this(attributesFactory, moduleVersionId, attributesFactory.mutable());
    }

    private VariantMetadataRules(ImmutableAttributesFactory attributesFactory, ModuleVersionIdentifier moduleVersionId, AttributeContainerInternal baseAttributes) {
        this.attributesFactory = attributesFactory;
        this.moduleVersionId = moduleVersionId;
        this.baseAttributes = baseAttributes;
    }

    public AttributeContainerInternal getAttributes(@Nullable String variantName) {
        if (variantName == null) {
            return baseAttributes;
        } else {
            return variantAttributes.computeIfAbsent(variantName, name -> attributesFactory.mutable(baseAttributes));
        }
    }

    protected AttributeContainerInternal joinVariantAttributes(VariantResolveMetadata variant, AttributeContainerInternal parent) {
        AttributeContainerInternal variantAttrs = getAttributes(variant.getName());
        return attributesFactory.join(parent, variantAttrs);
    }

    public ImmutableAttributes applyVariantAttributeRules(VariantResolveMetadata variant, AttributeContainerInternal parent) {
        AttributeContainerInternal joined = joinVariantAttributes(variant, parent);
        if (variantAttributesRules != null) {
            return variantAttributesRules.execute(variant, joined);
        }
        return joined.asImmutable();
    }

    public ImmutableCapabilities applyCapabilitiesRules(VariantResolveMetadata variant, ImmutableCapabilities capabilities) {
        if (capabilitiesRules != null) {
            DefaultMutableCapabilitiesMetadata mutableCapabilities;
            if (capabilities.asSet().isEmpty()) {
                // we must add the implicit capability here because it is assumed that if there's a rule
                // "addCapability" would effectively _add_ a capability, so the implicit one must not be forgotten
                mutableCapabilities = new DefaultMutableCapabilitiesMetadata(ImmutableCapabilities.of(
                    new DefaultImmutableCapability(moduleVersionId.getGroup(), moduleVersionId.getName(), moduleVersionId.getVersion()))
                );
            } else {
                mutableCapabilities = new DefaultMutableCapabilitiesMetadata(capabilities);
            }
            return capabilitiesRules.execute(variant, mutableCapabilities);
        }
        return capabilities;
    }

    public <T extends ModuleDependencyMetadata> List<? extends ModuleDependencyMetadata> applyDependencyMetadataRules(VariantResolveMetadata variant, List<T> configDependencies) {
        if (dependencyMetadataRules != null) {
            return dependencyMetadataRules.execute(variant, configDependencies);
        }
        return configDependencies;
    }

    public <T extends ComponentArtifactMetadata> ImmutableList<T> applyVariantFilesMetadataRulesToArtifacts(VariantResolveMetadata variant, ImmutableList<T> declaredArtifacts, ModuleComponentIdentifier componentIdentifier) {
        if (variantFilesRules != null) {
            return variantFilesRules.executeForArtifacts(variant, declaredArtifacts, componentIdentifier);
        }
        return declaredArtifacts;
    }

    public <T extends ComponentVariant.File> ImmutableList<T> applyVariantFilesMetadataRulesToFiles(VariantResolveMetadata variant, ImmutableList<T> declaredFiles, ModuleComponentIdentifier componentIdentifier) {
        if (variantFilesRules != null) {
            return variantFilesRules.executeForFiles(variant, declaredFiles, componentIdentifier);
        }
        return declaredFiles;
    }

    public void addDependencyAction(Instantiator instantiator, NotationParser<Object, DirectDependencyMetadata> dependencyNotationParser, NotationParser<Object, DependencyConstraintMetadata> dependencyConstraintNotationParser, VariantAction<? super DirectDependenciesMetadata> action) {
        if (dependencyMetadataRules == null) {
            dependencyMetadataRules = new DependencyMetadataRules(instantiator, dependencyNotationParser, dependencyConstraintNotationParser, attributesFactory);
        }
        dependencyMetadataRules.addDependencyAction(action);
    }

    public void addDependencyConstraintAction(Instantiator instantiator, NotationParser<Object, DirectDependencyMetadata> dependencyNotationParser, NotationParser<Object, DependencyConstraintMetadata> dependencyConstraintNotationParser, VariantAction<? super DependencyConstraintsMetadata> action) {
        if (dependencyMetadataRules == null) {
            dependencyMetadataRules = new DependencyMetadataRules(instantiator, dependencyNotationParser, dependencyConstraintNotationParser, attributesFactory);
        }
        dependencyMetadataRules.addDependencyConstraintAction(action);
    }

    public void addAttributesAction(ImmutableAttributesFactory attributesFactory, VariantAction<? super AttributeContainer> action) {
        if (variantAttributesRules == null) {
            variantAttributesRules = new VariantAttributesRules(attributesFactory);
        }
        variantAttributesRules.addAttributesAction(action);
    }

    public void addCapabilitiesAction(VariantAction<? super MutableCapabilitiesMetadata> action) {
        if (capabilitiesRules == null) {
            capabilitiesRules = new CapabilitiesRules();
        }
        capabilitiesRules.addCapabilitiesAction(action);
    }

    public void addVariantFilesAction(VariantAction<? super MutableVariantFilesMetadata> action) {
        if (variantFilesRules == null) {
            variantFilesRules = new VariantFilesRules();
        }
        variantFilesRules.addFilesAction(action);
    }

    public void addVariant(String name) {
        additionalVariants.add(new AdditionalVariant(name));
    }

    public void addVariant(String name, String basedOn, boolean lenient) {
        additionalVariants.add(new AdditionalVariant(name, basedOn, lenient));
    }

    public List<AdditionalVariant> getAdditionalVariants() {
        return additionalVariants;
    }

    public static VariantMetadataRules noOp() {
        return ImmutableRules.INSTANCE;
    }

    /**
     * A variant action is an action which is only executed if it matches the name of the variant.
     * @param <T> the type of the action subject
     */
    public static class VariantAction<T> {
        private final String variantName;
        private final Action<? super T> delegate;

        /**
         * @param variantName The variant name to match. If null, all variants are matched.
         * @param delegate The action to execute upon matching.
         */
        public VariantAction(@Nullable String variantName, Action<? super T> delegate) {
            this.variantName = variantName;
            this.delegate = delegate;
        }

        /**
         * Executes the underlying action if the supplied variant matches.
         *
         * @param variant the variant metadata, used to check if the rule applies
         * @param subject the subject of the rule
         */
        public void maybeExecute(VariantResolveMetadata variant, T subject) {
            if (variantName == null || variantName.equals(variant.getName())) {
                delegate.execute(subject);
            }
        }
    }

    private static class ImmutableRules extends VariantMetadataRules {
        private final static ImmutableRules INSTANCE = new ImmutableRules();

        private ImmutableRules() {
            super(null, null, null);
        }

        @Override
        public AttributeContainerInternal getAttributes(@Nullable String variantName) {
            return ImmutableAttributes.EMPTY;
        }

        @Override
        protected AttributeContainerInternal joinVariantAttributes(VariantResolveMetadata variant, AttributeContainerInternal parent) {
            return parent;
        }

        @Override
        public void addDependencyAction(Instantiator instantiator, NotationParser<Object, DirectDependencyMetadata> dependencyNotationParser, NotationParser<Object, DependencyConstraintMetadata> dependencyConstraintNotationParser, VariantAction<? super DirectDependenciesMetadata> action) {
            throw new UnsupportedOperationException("You are probably trying to add a dependency rule to something that wasn't supposed to be mutable");
        }

        @Override
        public void addDependencyConstraintAction(Instantiator instantiator, NotationParser<Object, DirectDependencyMetadata> dependencyNotationParser, NotationParser<Object, DependencyConstraintMetadata> dependencyConstraintNotationParser, VariantAction<? super DependencyConstraintsMetadata> action) {
            throw new UnsupportedOperationException("You are probably trying to add a dependency constraint rule to something that wasn't supposed to be mutable");
        }

        @Override
        public void addAttributesAction(ImmutableAttributesFactory attributesFactory, VariantAction<? super AttributeContainer> action) {
            throw new UnsupportedOperationException("You are probably trying to add a variant attribute to something that wasn't supposed to be mutable");
        }

        @Override
        public void addCapabilitiesAction(VariantAction<? super MutableCapabilitiesMetadata> action) {
            throw new UnsupportedOperationException("You are probably trying to change capabilities of something that wasn't supposed to be mutable");
        }
    }

}
