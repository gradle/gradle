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

import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.ComponentMetadata;
import org.gradle.api.artifacts.ComponentMetadataContext;
import org.gradle.api.artifacts.ComponentMetadataDetails;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.VariantMetadata;
import org.gradle.api.artifacts.ivy.IvyModuleDescriptor;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.ComponentMetadataProcessor;
import org.gradle.api.internal.artifacts.MetadataResolutionContext;
import org.gradle.api.internal.artifacts.ivyservice.DefaultIvyModuleDescriptor;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.UserProvidedMetadata;
import org.gradle.api.internal.artifacts.repositories.resolver.ComponentMetadataDetailsAdapter;
import org.gradle.api.internal.artifacts.repositories.resolver.DependencyConstraintMetadataImpl;
import org.gradle.api.internal.artifacts.repositories.resolver.DirectDependencyMetadataImpl;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.internal.action.ConfigurableRule;
import org.gradle.internal.action.DefaultConfigurableRules;
import org.gradle.internal.action.InstantiatingAction;
import org.gradle.internal.component.external.model.IvyModuleResolveMetadata;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata;
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.internal.resolve.caching.ComponentMetadataRuleExecutor;
import org.gradle.internal.rules.RuleAction;
import org.gradle.internal.rules.SpecRuleAction;
import org.gradle.internal.typeconversion.NotationParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class DefaultComponentMetadataProcessor implements ComponentMetadataProcessor {

    private static final Transformer<ModuleComponentResolveMetadata, WrappingComponentMetadataContext> DETAILS_TO_RESULT = new Transformer<ModuleComponentResolveMetadata, WrappingComponentMetadataContext>() {
            @Override
            public ModuleComponentResolveMetadata transform(WrappingComponentMetadataContext componentMetadataContext) {
                return componentMetadataContext.getMutableMetadata().asImmutable();
            }
        };

    private final Instantiator instantiator;
    private final NotationParser<Object, DirectDependencyMetadataImpl> dependencyMetadataNotationParser;
    private final NotationParser<Object, DependencyConstraintMetadataImpl> dependencyConstraintMetadataNotationParser;
    private final ImmutableAttributesFactory attributesFactory;
    private final ComponentMetadataRuleExecutor ruleExecutor;
    private final MetadataResolutionContext metadataResolutionContext;
    private final Set<SpecRuleAction<? super ComponentMetadataDetails>> rules;
    private final Set<SpecConfigurableRule> classBasedRules;

    public DefaultComponentMetadataProcessor(Set<SpecRuleAction<? super ComponentMetadataDetails>> rules,
                                             Set<SpecConfigurableRule> classBasedRules,
                                             Instantiator instantiator,
                                             NotationParser<Object, DirectDependencyMetadataImpl> dependencyMetadataNotationParser,
                                             NotationParser<Object, DependencyConstraintMetadataImpl> dependencyConstraintMetadataNotationParser,
                                             ImmutableAttributesFactory attributesFactory,
                                             ComponentMetadataRuleExecutor ruleExecutor,
                                             MetadataResolutionContext resolutionContext) {
        this.rules = rules;
        this.classBasedRules = classBasedRules;
        this.instantiator = instantiator;
        this.dependencyMetadataNotationParser = dependencyMetadataNotationParser;
        this.dependencyConstraintMetadataNotationParser = dependencyConstraintMetadataNotationParser;
        this.attributesFactory = attributesFactory;
        this.ruleExecutor = ruleExecutor;
        this.metadataResolutionContext = resolutionContext;
    }

    @Override
    public ModuleComponentResolveMetadata processMetadata(ModuleComponentResolveMetadata metadata) {
        ModuleComponentResolveMetadata updatedMetadata;
        if (rules.isEmpty() && classBasedRules.isEmpty()) {
            updatedMetadata = metadata;
        } else if (rules.isEmpty()) {
            updatedMetadata = processClassRuleWithCaching(metadata, metadataResolutionContext);
        } else {
            MutableModuleComponentResolveMetadata mutableMetadata = metadata.asMutable();
            ComponentMetadataDetails details = instantiator.newInstance(ComponentMetadataDetailsAdapter.class, mutableMetadata, instantiator, dependencyMetadataNotationParser, dependencyConstraintMetadataNotationParser);
            processAllRules(metadata, details, metadata.getModuleVersionId());
            updatedMetadata = mutableMetadata.asImmutable();
        }

        if (!updatedMetadata.getStatusScheme().contains(updatedMetadata.getStatus())) {
            throw new ModuleVersionResolveException(updatedMetadata.getModuleVersionId(), String.format("Unexpected status '%s' specified for %s. Expected one of: %s", updatedMetadata.getStatus(), updatedMetadata.getId().getDisplayName(), updatedMetadata.getStatusScheme()));
        }
        return updatedMetadata;
    }

    @Override
    public ComponentMetadata processMetadata(ComponentMetadata metadata) {
        ComponentMetadata updatedMetadata;
        if (rules.isEmpty() && classBasedRules.isEmpty()) {
            updatedMetadata = metadata;
        } else {
            ShallowComponentMetadataAdapter details = new ShallowComponentMetadataAdapter(metadata, attributesFactory);
            processAllRules(null, details, metadata.getId());
            updatedMetadata = details.asImmutable();
        }
        if (!updatedMetadata.getStatusScheme().contains(updatedMetadata.getStatus())) {
            throw new ModuleVersionResolveException(updatedMetadata.getId(), String.format("Unexpected status '%s' specified for %s. Expected one of: %s", updatedMetadata.getStatus(), updatedMetadata.getId().toString(), updatedMetadata.getStatusScheme()));
        }
        return updatedMetadata;
    }

    private void processAllRules(ModuleComponentResolveMetadata metadata, ComponentMetadataDetails details, ModuleVersionIdentifier id) {
        for (SpecRuleAction<? super ComponentMetadataDetails> rule : rules) {
            processRule(rule, metadata, details);
        }
        processClassRule(metadata, details, id, metadataResolutionContext.getInjectingInstantiator());
    }

    private void processClassRule(final ModuleComponentResolveMetadata metadata, final ComponentMetadataDetails details, ModuleVersionIdentifier id, Instantiator instantiator) {
        InstantiatingAction<ComponentMetadataContext> action = collectRulesAndCreateAction(id, instantiator);

        DefaultComponentMetadataContext componentMetadataContext = new DefaultComponentMetadataContext(details, metadata);
        try {
            action.execute(componentMetadataContext);
        } catch (InvalidUserCodeException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidUserCodeException(String.format("There was an error while evaluating a component metadata rule for %s.", details.getId()), e);
        }
    }

    private ModuleComponentResolveMetadata processClassRuleWithCaching(final ModuleComponentResolveMetadata metadata, MetadataResolutionContext metadataResolutionContext) {
        InstantiatingAction<ComponentMetadataContext> action = collectRulesAndCreateAction(metadata.getModuleVersionId(), metadataResolutionContext.getInjectingInstantiator());
        try {
            return ruleExecutor.execute(metadata, action, DETAILS_TO_RESULT,
                new Transformer<WrappingComponentMetadataContext, ModuleComponentResolveMetadata>() {
                    @Override
                    public WrappingComponentMetadataContext transform(ModuleComponentResolveMetadata moduleVersionIdentifier) {
                        return new WrappingComponentMetadataContext(metadata, instantiator, dependencyMetadataNotationParser, dependencyConstraintMetadataNotationParser);
                    }
                }, metadataResolutionContext.getCachePolicy());
        } catch (InvalidUserCodeException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidUserCodeException(String.format("There was an error while evaluating a component metadata rule for %s.", metadata.getModuleVersionId()), e);
        }
    }

    private InstantiatingAction<ComponentMetadataContext> collectRulesAndCreateAction(ModuleVersionIdentifier id, Instantiator instantiator) {
        ArrayList<ConfigurableRule<ComponentMetadataContext>> rules = new ArrayList<ConfigurableRule<ComponentMetadataContext>>();
        for (SpecConfigurableRule classBasedRule : classBasedRules) {
            if (classBasedRule.getSpec().isSatisfiedBy(id)) {
                rules.add(classBasedRule.getConfigurableRule());
            }
        }
        return new InstantiatingAction<ComponentMetadataContext>(new DefaultConfigurableRules<ComponentMetadataContext>(rules), instantiator, new ExceptionHandler());
    }


    private void processRule(SpecRuleAction<? super ComponentMetadataDetails> specRuleAction, ModuleComponentResolveMetadata metadata, final ComponentMetadataDetails details) {
        if (!specRuleAction.getSpec().isSatisfiedBy(details)) {
            return;
        }

        final List<Object> inputs = Lists.newArrayList();
        final RuleAction<? super ComponentMetadataDetails> action = specRuleAction.getAction();
        for (Class<?> inputType : action.getInputTypes()) {
            if (inputType == IvyModuleDescriptor.class) {
                // Ignore the rule if it expects Ivy metadata and this isn't an Ivy module
                if (!(metadata instanceof IvyModuleResolveMetadata)) {
                    return;
                }

                IvyModuleResolveMetadata ivyMetadata = (IvyModuleResolveMetadata) metadata;
                inputs.add(new DefaultIvyModuleDescriptor(ivyMetadata.getExtraAttributes(), ivyMetadata.getBranch(), ivyMetadata.getStatus()));
                continue;
            }

            // We've already validated the inputs: should never get here.
            throw new IllegalStateException();
        }

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
        private AttributeContainerInternal attributes;

        public ShallowComponentMetadataAdapter(ComponentMetadata source, ImmutableAttributesFactory attributesFactory) {
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
