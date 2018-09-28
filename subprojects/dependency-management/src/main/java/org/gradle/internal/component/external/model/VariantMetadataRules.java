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
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.capabilities.CapabilitiesMetadata;
import org.gradle.api.artifacts.DependencyConstraintMetadata;
import org.gradle.api.artifacts.DependencyConstraintsMetadata;
import org.gradle.api.artifacts.DirectDependenciesMetadata;
import org.gradle.api.artifacts.DirectDependencyMetadata;
import org.gradle.api.capabilities.MutableCapabilitiesMetadata;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.specs.Spec;
import org.gradle.internal.Cast;
import org.gradle.internal.component.model.CapabilitiesRules;
import org.gradle.internal.component.model.DependencyMetadataRules;
import org.gradle.internal.component.model.VariantAttributesRules;
import org.gradle.internal.component.model.VariantResolveMetadata;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.NotationParser;

import java.util.Iterator;
import java.util.List;

public class VariantMetadataRules {
    private final ImmutableAttributesFactory attributesFactory;
    private DependencyMetadataRules dependencyMetadataRules;
    private VariantAttributesRules variantAttributesRules;
    private CapabilitiesRules capabilitiesRules;
    private VariantDerivationStrategy variantDerivationStrategy = new NoOpDerivationStrategy();

    public VariantMetadataRules(ImmutableAttributesFactory attributesFactory) {
        this.attributesFactory = attributesFactory;
    }

    public VariantDerivationStrategy getVariantDerivationStrategy() {
        return variantDerivationStrategy;
    }

    public void setVariantDerivationStrategy(VariantDerivationStrategy variantDerivationStrategy) {
        this.variantDerivationStrategy = variantDerivationStrategy;
    }

    public ImmutableAttributes applyVariantAttributeRules(VariantResolveMetadata variant, AttributeContainerInternal source) {
        if (variantAttributesRules != null) {
            return variantAttributesRules.execute(variant, source);
        }
        return source.asImmutable();
    }

    public CapabilitiesMetadata applyCapabilitiesRules(VariantResolveMetadata variant, CapabilitiesMetadata capabilities) {
        if (capabilitiesRules != null) {
            MutableCapabilities mutableCapabilities = new MutableCapabilities(Lists.newArrayList(capabilities.getCapabilities()));
            return capabilitiesRules.execute(variant, mutableCapabilities);
        }
        return capabilities;
    }

    public <T extends ModuleDependencyMetadata> List<T> applyDependencyMetadataRules(VariantResolveMetadata variant, List<T> configDependencies) {
        if (dependencyMetadataRules != null) {
            return dependencyMetadataRules.execute(variant, configDependencies);
        }
        return configDependencies;
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

    public static VariantMetadataRules noOp() {
        return ImmutableRules.INSTANCE;
    }

    /**
     * A variant action is an action which is only executed if it matches a predicate. Typically, on the name
     * of the variant or its attributes.
     * @param <T> the type of the action subject
     */
    public static class VariantAction<T> {
        private final Spec<? super VariantResolveMetadata> spec;
        private final Action<? super T> delegate;

        public VariantAction(Spec<? super VariantResolveMetadata> spec, Action<? super T> delegate) {
            this.spec = spec;
            this.delegate = delegate;
        }

        /**
         * Executes the underlying action if the supplied variant matches the predicate
         * @param variant the variant metadata, used to check if the rule applies
         * @param subject the subject of the rule
         */
        public void maybeExecute(VariantResolveMetadata variant, T subject) {
            if (spec.isSatisfiedBy(variant)) {
                delegate.execute(subject);
            }
        }
    }

    private static class ImmutableRules extends VariantMetadataRules {
        private final static ImmutableRules INSTANCE = new ImmutableRules();

        private ImmutableRules() {
            super(null);
        }

        @Override
        public void setVariantDerivationStrategy(VariantDerivationStrategy variantDerivationStrategy) {
            throw new UnsupportedOperationException("You are probably to set the derivation strategy to something that wasn't supposed to be mutable");
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

    private static class MutableCapabilities implements MutableCapabilitiesMetadata {
        private final List<Capability> descriptors;

        private MutableCapabilities(List<Capability> descriptors) {
            this.descriptors = descriptors;
        }

        @Override
        public void addCapability(String group, String name, String version) {
            for (Capability descriptor : descriptors) {
                if (descriptor.getGroup().equals(group) && descriptor.getName().equals(name) && !descriptor.getVersion().equals(version)) {
                    throw new InvalidUserDataException("Cannot add capability " + group + ":" + name + " with version " + version + " because it's already defined with version " + descriptor.getVersion());
                }
            }
            descriptors.add(new ImmutableCapability(group, name, version));
        }

        @Override
        public void removeCapability(String group, String name) {
            Iterator<Capability> it = descriptors.iterator();
            while (it.hasNext()) {
                Capability next = it.next();
                if (next.getGroup().equals(group) && next.getName().equals(name)) {
                    it.remove();
                }
            }
        }

        @Override
        public CapabilitiesMetadata asImmutable() {
            ImmutableList<ImmutableCapability> capabilities = Cast.uncheckedCast(getCapabilities());
            return new ImmutableCapabilities(capabilities);
        }

        @Override
        public List<? extends Capability> getCapabilities() {
            return ImmutableList.copyOf(descriptors);
        }

    }

}
