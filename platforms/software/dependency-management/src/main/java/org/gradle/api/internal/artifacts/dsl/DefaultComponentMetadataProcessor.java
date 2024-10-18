/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.artifacts.dsl;

import org.gradle.api.Action;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.ComponentMetadata;
import org.gradle.api.artifacts.ComponentMetadataContext;
import org.gradle.api.artifacts.ComponentMetadataDetails;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.VariantMetadata;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.ComponentMetadataProcessor;
import org.gradle.api.internal.artifacts.MetadataResolutionContext;
import org.gradle.api.internal.artifacts.dsl.dependencies.PlatformSupport;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.UserProvidedMetadata;
import org.gradle.api.internal.artifacts.repositories.resolver.ComponentMetadataDetailsAdapter;
import org.gradle.api.internal.artifacts.repositories.resolver.DependencyConstraintMetadataImpl;
import org.gradle.api.internal.artifacts.repositories.resolver.DirectDependencyMetadataImpl;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.AttributesFactory;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.Actions;
import org.gradle.internal.action.ConfigurableRule;
import org.gradle.internal.action.DefaultConfigurableRules;
import org.gradle.internal.action.InstantiatingAction;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata;
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata;
import org.gradle.internal.component.external.model.VariantDerivationStrategy;
import org.gradle.internal.component.external.model.ivy.DefaultIvyModuleResolveMetadata;
import org.gradle.internal.component.external.model.ivy.RealisedIvyModuleResolveMetadata;
import org.gradle.internal.component.external.model.maven.DefaultMavenModuleResolveMetadata;
import org.gradle.internal.component.external.model.maven.RealisedMavenModuleResolveMetadata;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.internal.resolve.caching.ComponentMetadataRuleExecutor;
import org.gradle.internal.rules.RuleAction;
import org.gradle.internal.rules.SpecRuleAction;
import org.gradle.internal.serialize.InputStreamBackedDecoder;
import org.gradle.internal.serialize.OutputStreamBackedEncoder;
import org.gradle.internal.serialize.Serializer;
import org.gradle.internal.typeconversion.NotationParser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class DefaultComponentMetadataProcessor implements ComponentMetadataProcessor {

    private final static boolean FORCE_REALIZE = Boolean.getBoolean("org.gradle.integtest.force.realize.metadata");

    private static final Transformer<ModuleComponentResolveMetadata, WrappingComponentMetadataContext> DETAILS_TO_RESULT = componentMetadataContext -> {
        ModuleComponentResolveMetadata metadata = componentMetadataContext
            .getImmutableMetadataWithDerivationStrategy(componentMetadataContext.getVariantDerivationStrategy());
        return realizeMetadata(metadata);
    };

    private ModuleComponentResolveMetadata maybeForceRealisation(ModuleComponentResolveMetadata metadata) {
        if (FORCE_REALIZE) {
            metadata = realizeMetadata(metadata);
            metadata = forceSerialization(metadata);
        }
        return metadata;
    }

    private static ModuleComponentResolveMetadata realizeMetadata(ModuleComponentResolveMetadata metadata) {
        if (metadata instanceof DefaultIvyModuleResolveMetadata) {
            metadata = RealisedIvyModuleResolveMetadata.transform((DefaultIvyModuleResolveMetadata) metadata);
        } else if (metadata instanceof DefaultMavenModuleResolveMetadata) {
            metadata = RealisedMavenModuleResolveMetadata.transform((DefaultMavenModuleResolveMetadata) metadata);
        } else {
            throw new IllegalStateException("Invalid type received: " + metadata.getClass());
        }
        return metadata;
    }

    private ModuleComponentResolveMetadata forceSerialization(ModuleComponentResolveMetadata metadata) {
        Serializer<ModuleComponentResolveMetadata> serializer = ruleExecutor.getComponentMetadataContextSerializer();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        byte[] bytes;
        try {
            serializer.write(new OutputStreamBackedEncoder(byteArrayOutputStream), metadata);
            bytes = byteArrayOutputStream.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            try {
                byteArrayOutputStream.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        try {
            ModuleComponentResolveMetadata forceRead = serializer.read(new InputStreamBackedDecoder(new ByteArrayInputStream(bytes)));
            // TODO: CC cannot enable this assertion because moduleSource is not serialized, so doesn't appear in the deserialized form
            //assert metadata.equals(forceRead);
            metadata = forceRead;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return metadata;
    }

    private final Instantiator instantiator;
    private final NotationParser<Object, DirectDependencyMetadataImpl> dependencyMetadataNotationParser;
    private final NotationParser<Object, DependencyConstraintMetadataImpl> dependencyConstraintMetadataNotationParser;
    private final NotationParser<Object, ComponentIdentifier> componentIdentifierNotationParser;
    private final AttributesFactory attributesFactory;
    private final ComponentMetadataRuleExecutor ruleExecutor;
    private final MetadataResolutionContext metadataResolutionContext;
    private final ComponentMetadataRuleContainer metadataRuleContainer;
    private final PlatformSupport platformSupport;

    public DefaultComponentMetadataProcessor(ComponentMetadataRuleContainer metadataRuleContainer,
                                             Instantiator instantiator,
                                             NotationParser<Object, DirectDependencyMetadataImpl> dependencyMetadataNotationParser,
                                             NotationParser<Object, DependencyConstraintMetadataImpl> dependencyConstraintMetadataNotationParser,
                                             NotationParser<Object, ComponentIdentifier> componentIdentifierNotationParser,
                                             AttributesFactory attributesFactory,
                                             ComponentMetadataRuleExecutor ruleExecutor,
                                             PlatformSupport platformSupport,
                                             MetadataResolutionContext resolutionContext) {
        this.metadataRuleContainer = metadataRuleContainer;
        this.instantiator = instantiator;
        this.dependencyMetadataNotationParser = dependencyMetadataNotationParser;
        this.dependencyConstraintMetadataNotationParser = dependencyConstraintMetadataNotationParser;
        this.componentIdentifierNotationParser = componentIdentifierNotationParser;
        this.attributesFactory = attributesFactory;
        this.ruleExecutor = ruleExecutor;
        this.platformSupport = platformSupport;
        this.metadataResolutionContext = resolutionContext;
    }

    @Override
    public ModuleComponentResolveMetadata processMetadata(ModuleComponentResolveMetadata origin) {
        VariantDerivationStrategy curStrategy = metadataRuleContainer.getVariantDerivationStrategy();
        ModuleComponentResolveMetadata metadata = origin.withDerivationStrategy(curStrategy);
        ModuleComponentResolveMetadata updatedMetadata;
        if (metadataRuleContainer.isEmpty()) {
            updatedMetadata = maybeForceRealisation(metadata);
        } else if (metadataRuleContainer.isClassBasedRulesOnly()) {
            Action<ComponentMetadataContext> action = collectRulesAndCreateAction(metadataRuleContainer.getOnlyClassRules(), metadata.getModuleVersionId(), metadataResolutionContext.getInjectingInstantiator());
            if (action instanceof InstantiatingAction) {
                InstantiatingAction<ComponentMetadataContext> ia = (InstantiatingAction<ComponentMetadataContext>) action;
                if (shouldCacheComponentMetadataRule(ia, metadata)) {
                    updatedMetadata = processClassRuleWithCaching(ia, metadata, metadataResolutionContext);
                } else {
                    MutableModuleComponentResolveMetadata mutableMetadata = metadata.asMutable();
                    processClassRule(action, metadata, createDetails(mutableMetadata));
                    updatedMetadata = maybeForceRealisation(mutableMetadata.asImmutable());
                }
            } else {
                updatedMetadata = maybeForceRealisation(metadata);
            }
        } else {
            MutableModuleComponentResolveMetadata mutableMetadata = metadata.asMutable();
            ComponentMetadataDetails details = createDetails(mutableMetadata);
            processAllRules(metadata, details, metadata.getModuleVersionId());
            updatedMetadata = maybeForceRealisation(mutableMetadata.asImmutable());
        }

        if (!updatedMetadata.getStatusScheme().contains(updatedMetadata.getStatus())) {
            throw new ModuleVersionResolveException(updatedMetadata.getModuleVersionId(), () -> String.format("Unexpected status '%s' specified for %s. Expected one of: %s", updatedMetadata.getStatus(), updatedMetadata.getId().getDisplayName(), updatedMetadata.getStatusScheme()));
        }
        return updatedMetadata;
    }

    private boolean shouldCacheComponentMetadataRule(InstantiatingAction<ComponentMetadataContext> action, ModuleComponentResolveMetadata metadata) {
        return action.getRules().isCacheable() && metadata.isComponentMetadataRuleCachingEnabled();
    }

    protected ComponentMetadataDetails createDetails(MutableModuleComponentResolveMetadata mutableMetadata) {
        return instantiator.newInstance(ComponentMetadataDetailsAdapter.class, mutableMetadata, instantiator, dependencyMetadataNotationParser, dependencyConstraintMetadataNotationParser, componentIdentifierNotationParser, platformSupport);
    }

    @Override
    public ComponentMetadata processMetadata(ComponentMetadata metadata) {
        ComponentMetadata updatedMetadata;
        if (metadataRuleContainer.isEmpty()) {
            updatedMetadata = metadata;
        } else {
            ShallowComponentMetadataAdapter details = new ShallowComponentMetadataAdapter(metadata, attributesFactory);
            processAllRules(null, details, metadata.getId());
            updatedMetadata = details.asImmutable();
        }
        if (!updatedMetadata.getStatusScheme().contains(updatedMetadata.getStatus())) {
            throw new ModuleVersionResolveException(updatedMetadata.getId(), () -> String.format("Unexpected status '%s' specified for %s. Expected one of: %s", updatedMetadata.getStatus(), updatedMetadata.getId().toString(), updatedMetadata.getStatusScheme()));
        }
        return updatedMetadata;
    }

    @Override
    public int getRulesHash() {
        return metadataRuleContainer.getRulesHash();
    }

    private void processAllRules(ModuleComponentResolveMetadata metadata, ComponentMetadataDetails details, ModuleVersionIdentifier id) {
        for (MetadataRuleWrapper wrapper : metadataRuleContainer) {
            if (wrapper.isClassBased()) {
                Collection<SpecConfigurableRule> rules = wrapper.getClassRules();
                Action<ComponentMetadataContext> action = collectRulesAndCreateAction(rules, id, metadataResolutionContext.getInjectingInstantiator());
                processClassRule(action, metadata, details);
            } else {
                processRule(wrapper.getRule(), metadata, details);
            }
        }
    }

    private void processClassRule(Action<ComponentMetadataContext> action, final ModuleComponentResolveMetadata metadata, final ComponentMetadataDetails details) {
        DefaultComponentMetadataContext componentMetadataContext = new DefaultComponentMetadataContext(details, metadata);
        try {
            action.execute(componentMetadataContext);
        } catch (InvalidUserCodeException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidUserCodeException(String.format("There was an error while evaluating a component metadata rule for %s.", details.getId()), e);
        }
    }

    private ModuleComponentResolveMetadata processClassRuleWithCaching(InstantiatingAction<ComponentMetadataContext> action, final ModuleComponentResolveMetadata metadata, MetadataResolutionContext metadataResolutionContext) {
        try {
            return ruleExecutor.execute(metadata, action, DETAILS_TO_RESULT,
                moduleVersionIdentifier -> new WrappingComponentMetadataContext(metadata, instantiator, dependencyMetadataNotationParser, dependencyConstraintMetadataNotationParser, componentIdentifierNotationParser, platformSupport), metadataResolutionContext.getCachePolicy());
        } catch (InvalidUserCodeException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidUserCodeException(String.format("There was an error while evaluating a component metadata rule for %s.", metadata.getModuleVersionId()), e);
        }
    }

    private Action<ComponentMetadataContext> collectRulesAndCreateAction(Collection<SpecConfigurableRule> rules, ModuleVersionIdentifier id, Instantiator instantiator) {
        if (rules.isEmpty()) {
            return Actions.doNothing();
        }
        ArrayList<ConfigurableRule<ComponentMetadataContext>> collectedRules = new ArrayList<>();
        for (SpecConfigurableRule classBasedRule : rules) {
            if (classBasedRule.getSpec().isSatisfiedBy(id)) {
                collectedRules.add(classBasedRule.getConfigurableRule());
            }
        }
        return new InstantiatingAction<>(new DefaultConfigurableRules<>(collectedRules), instantiator, new ExceptionHandler());
    }


    private void processRule(SpecRuleAction<? super ComponentMetadataDetails> specRuleAction, ModuleComponentResolveMetadata metadata, final ComponentMetadataDetails details) {
        if (!specRuleAction.getSpec().isSatisfiedBy(details)) {
            return;
        }
        final RuleAction<? super ComponentMetadataDetails> action = specRuleAction.getAction();
        if (!shouldExecute(action, metadata)) {
            return;
        }

        List<?> inputs = gatherAdditionalInputs(action, metadata);
        executeAction(action, inputs, details);
    }

    private void executeAction(RuleAction<? super ComponentMetadataDetails> action, List<?> inputs, ComponentMetadataDetails details) {
        try {
            synchronized (this) {
                action.execute(details, inputs);
            }
        } catch (InvalidUserCodeException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidUserCodeException(String.format("There was an error while evaluating a component metadata rule for %s.", details.getId()), e);
        }
    }

    private boolean shouldExecute(RuleAction<? super ComponentMetadataDetails> action, ModuleComponentResolveMetadata metadata) {
        List<Class<?>> inputTypes = action.getInputTypes();
        if (!inputTypes.isEmpty()) {
            return inputTypes.stream().anyMatch(input -> MetadataDescriptorFactory.isMatchingMetadata(input, metadata));
        }
        return true;
    }

    private List<?> gatherAdditionalInputs(RuleAction<? super ComponentMetadataDetails> action, ModuleComponentResolveMetadata metadata) {
        final List<Object> inputs = new ArrayList<>();
        for (Class<?> inputType : action.getInputTypes()) {
            MetadataDescriptorFactory descriptorFactory = new MetadataDescriptorFactory(metadata);
            Object descriptor = descriptorFactory.createDescriptor(inputType);
            if (descriptor != null) {
                inputs.add(descriptor);
            }
        }
        return inputs;
    }

    private static class ExceptionHandler implements InstantiatingAction.ExceptionHandler<ComponentMetadataContext> {

        @Override
        public void handleException(ComponentMetadataContext context, Throwable throwable) {
            throw new InvalidUserCodeException(String.format("There was an error while evaluating a component metadata rule for %s.", context.getDetails().getId()), throwable);
        }
    }

    static class ShallowComponentMetadataAdapter implements ComponentMetadataDetails {
        private final ModuleVersionIdentifier id;
        private boolean changing;
        private List<String> statusScheme;
        private final AttributeContainerInternal attributes;

        public ShallowComponentMetadataAdapter(ComponentMetadata source, AttributesFactory attributesFactory) {
            id = source.getId();
            changing = source.isChanging();
            statusScheme = source.getStatusScheme();
            attributes = attributesFactory.mutable((AttributeContainerInternal) source.getAttributes());
        }

        @Override
        public void setChanging(boolean changing) {
            this.changing = changing;
        }

        @Override
        public void setStatus(String status) {
            this.attributes.attribute(ProjectInternal.STATUS_ATTRIBUTE, status);
        }

        @Override
        public void setStatusScheme(List<String> statusScheme) {
            this.statusScheme = statusScheme;
        }

        @Override
        public void withVariant(String name, Action<? super VariantMetadata> action) {

        }

        @Override
        public void allVariants(Action<? super VariantMetadata> action) {

        }

        @Override
        public void addVariant(String name, Action<? super VariantMetadata> action) {

        }

        @Override
        public void addVariant(String name, String base, Action<? super VariantMetadata> action) {

        }

        @Override
        public void maybeAddVariant(String name, String base, Action<? super VariantMetadata> action) {

        }

        @Override
        public void belongsTo(Object notation) {

        }

        @Override
        public void belongsTo(Object notation, boolean virtual) {

        }

        @Override
        public ModuleVersionIdentifier getId() {
            return id;
        }

        @Override
        public boolean isChanging() {
            return changing;
        }

        @Override
        public String getStatus() {
            return attributes.getAttribute(ProjectInternal.STATUS_ATTRIBUTE);
        }

        @Override
        public List<String> getStatusScheme() {
            return statusScheme;
        }

        @Override
        public ComponentMetadataDetails attributes(Action<? super AttributeContainer> action) {
            action.execute(attributes);
            return this;
        }

        @Override
        public AttributeContainer getAttributes() {
            return attributes;
        }

        public ComponentMetadata asImmutable() {
            return new UserProvidedMetadata(id, statusScheme, attributes.asImmutable());
        }
    }
}
