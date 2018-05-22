/*
 * Copyright 2013 the original author or authors.
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

import com.google.common.collect.Interner;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.ActionConfiguration;
import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.ComponentMetadataContext;
import org.gradle.api.artifacts.ComponentMetadata;
import org.gradle.api.artifacts.ComponentMetadataDetails;
import org.gradle.api.artifacts.ComponentMetadataRule;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.VariantMetadata;
import org.gradle.api.artifacts.dsl.ComponentMetadataHandler;
import org.gradle.api.artifacts.ivy.IvyModuleDescriptor;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.internal.artifacts.ComponentMetadataProcessor;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.configurations.dynamicversion.CachePolicy;
import org.gradle.api.internal.artifacts.ivyservice.DefaultIvyModuleDescriptor;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.UserProvidedMetadata;
import org.gradle.api.internal.artifacts.repositories.resolver.ComponentMetadataDetailsAdapter;
import org.gradle.api.internal.artifacts.repositories.resolver.DependencyConstraintMetadataImpl;
import org.gradle.api.internal.artifacts.repositories.resolver.DirectDependencyMetadataImpl;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.internal.changedetection.state.isolation.IsolatableFactory;
import org.gradle.api.internal.notations.DependencyMetadataNotationParser;
import org.gradle.api.internal.notations.ModuleIdentifierNotationConverter;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.internal.action.ConfigurableRule;
import org.gradle.internal.action.DefaultConfigurableRule;
import org.gradle.internal.action.DefaultConfigurableRules;
import org.gradle.internal.component.external.model.IvyModuleResolveMetadata;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata;
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata;
import org.gradle.internal.action.InstantiatingAction;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.internal.resolve.caching.ComponentMetadataRuleExecutor;
import org.gradle.internal.rules.DefaultRuleActionAdapter;
import org.gradle.internal.rules.DefaultRuleActionValidator;
import org.gradle.internal.rules.RuleAction;
import org.gradle.internal.rules.RuleActionAdapter;
import org.gradle.internal.rules.RuleActionValidator;
import org.gradle.internal.rules.SpecRuleAction;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.internal.typeconversion.NotationParserBuilder;
import org.gradle.internal.typeconversion.UnsupportedNotationException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class DefaultComponentMetadataHandler implements ComponentMetadataHandler, ComponentMetadataProcessor {
    private static final String ADAPTER_NAME = ComponentMetadataHandler.class.getSimpleName();
    private static final List<Class<?>> VALIDATOR_PARAM_LIST = Collections.<Class<?>>singletonList(IvyModuleDescriptor.class);
    private static final String INVALID_SPEC_ERROR = "Could not add a component metadata rule for module '%s'.";

    private final Instantiator instantiator;
    private final Set<SpecRuleAction<? super ComponentMetadataDetails>> rules = Sets.newLinkedHashSet();
    private final Set<SpecConfigurableRule> classBasedRules = Sets.newLinkedHashSet();
    private final RuleActionAdapter ruleActionAdapter;
    private final NotationParser<Object, ModuleIdentifier> moduleIdentifierNotationParser;
    private final NotationParser<Object, DirectDependencyMetadataImpl> dependencyMetadataNotationParser;
    private final NotationParser<Object, DependencyConstraintMetadataImpl> dependencyConstraintMetadataNotationParser;
    private final ImmutableAttributesFactory attributesFactory;
    private final IsolatableFactory isolatableFactory;
    private final ComponentMetadataRuleExecutor ruleExecutor;

    DefaultComponentMetadataHandler(Instantiator instantiator,
                                    RuleActionAdapter ruleActionAdapter,
                                    ImmutableModuleIdentifierFactory moduleIdentifierFactory,
                                    Interner<String> stringInterner,
                                    ImmutableAttributesFactory attributesFactory,
                                    IsolatableFactory isolatableFactory,
                                    ComponentMetadataRuleExecutor ruleExecutor) {
        this.instantiator = instantiator;
        this.ruleActionAdapter = ruleActionAdapter;
        this.moduleIdentifierNotationParser = NotationParserBuilder
            .toType(ModuleIdentifier.class)
            .converter(new ModuleIdentifierNotationConverter(moduleIdentifierFactory))
            .toComposite();
        this.ruleExecutor = ruleExecutor;
        this.dependencyMetadataNotationParser = DependencyMetadataNotationParser.parser(instantiator, DirectDependencyMetadataImpl.class, stringInterner);
        this.dependencyConstraintMetadataNotationParser = DependencyMetadataNotationParser.parser(instantiator, DependencyConstraintMetadataImpl.class, stringInterner);
        this.attributesFactory = attributesFactory;
        this.isolatableFactory = isolatableFactory;
    }

    public DefaultComponentMetadataHandler(Instantiator instantiator, ImmutableModuleIdentifierFactory moduleIdentifierFactory, Interner<String> stringInterner, ImmutableAttributesFactory attributesFactory, IsolatableFactory isolatableFactory, ComponentMetadataRuleExecutor ruleExecutor) {
        this(instantiator, createAdapter(), moduleIdentifierFactory, stringInterner, attributesFactory, isolatableFactory, ruleExecutor);
    }

    private static RuleActionAdapter createAdapter() {
        RuleActionValidator ruleActionValidator = new DefaultRuleActionValidator(VALIDATOR_PARAM_LIST);
        return new DefaultRuleActionAdapter(ruleActionValidator, ADAPTER_NAME);
    }

    private ComponentMetadataHandler addRule(SpecRuleAction<? super ComponentMetadataDetails> ruleAction) {
        if (!classBasedRules.isEmpty()) {
            throw new IllegalArgumentException("Non class based component metadata rules must all be added before class based ones.");
        }
        rules.add(ruleAction);
        return this;
    }

    private ComponentMetadataHandler addClassBasedRule(SpecConfigurableRule ruleAction) {
        classBasedRules.add(ruleAction);
        return this;
    }

    private <U> SpecRuleAction<? super U> createAllSpecRuleAction(RuleAction<? super U> ruleAction) {
        return new SpecRuleAction<U>(ruleAction, Specs.<U>satisfyAll());
    }

    private SpecRuleAction<? super ComponentMetadataDetails> createSpecRuleActionForModule(Object id, RuleAction<? super ComponentMetadataDetails> ruleAction) {
        ModuleIdentifier moduleIdentifier;

        try {
            moduleIdentifier = moduleIdentifierNotationParser.parseNotation(id);
        } catch (UnsupportedNotationException e) {
            throw new InvalidUserCodeException(String.format(INVALID_SPEC_ERROR, id == null ? "null" : id.toString()), e);
        }

        Spec<ComponentMetadataDetails> spec = new ComponentMetadataDetailsMatchingSpec(moduleIdentifier);
        return new SpecRuleAction<ComponentMetadataDetails>(ruleAction, spec);
    }

    public ComponentMetadataHandler all(Action<? super ComponentMetadataDetails> rule) {
        return addRule(createAllSpecRuleAction(ruleActionAdapter.createFromAction(rule)));
    }

    public ComponentMetadataHandler all(Closure<?> rule) {
        return addRule(createAllSpecRuleAction(ruleActionAdapter.createFromClosure(ComponentMetadataDetails.class, rule)));
    }

    public ComponentMetadataHandler all(Object ruleSource) {
        return addRule(createAllSpecRuleAction(ruleActionAdapter.createFromRuleSource(ComponentMetadataDetails.class, ruleSource)));
    }

    public ComponentMetadataHandler withModule(Object id, Action<? super ComponentMetadataDetails> rule) {
        return addRule(createSpecRuleActionForModule(id, ruleActionAdapter.createFromAction(rule)));
    }

    public ComponentMetadataHandler withModule(Object id, Closure<?> rule) {
        return addRule(createSpecRuleActionForModule(id, ruleActionAdapter.createFromClosure(ComponentMetadataDetails.class, rule)));
    }

    public ComponentMetadataHandler withModule(Object id, Object ruleSource) {
        return addRule(createSpecRuleActionForModule(id, ruleActionAdapter.createFromRuleSource(ComponentMetadataDetails.class, ruleSource)));
    }

    @Override
    public ComponentMetadataHandler all(Class<? extends ComponentMetadataRule> rule) {
        return addClassBasedRule(createAllSpecConfigurableRule(DefaultConfigurableRule.<ComponentMetadataContext>of(rule)));
    }

    @Override
    public ComponentMetadataHandler all(Class<? extends ComponentMetadataRule> rule, Action<? super ActionConfiguration> configureAction) {
        return addClassBasedRule(createAllSpecConfigurableRule(DefaultConfigurableRule.<ComponentMetadataContext>of(rule, configureAction, isolatableFactory)));
    }

    @Override
    public ComponentMetadataHandler withModule(Object id, Class<? extends ComponentMetadataRule> rule) {
        return addClassBasedRule(createModuleSpecConfigurableRule(id, DefaultConfigurableRule.<ComponentMetadataContext>of(rule)));
    }

    @Override
    public ComponentMetadataHandler withModule(Object id, Class<? extends ComponentMetadataRule> rule, Action<? super ActionConfiguration> configureAction) {
        return addClassBasedRule(createModuleSpecConfigurableRule(id, DefaultConfigurableRule.<ComponentMetadataContext>of(rule, configureAction, isolatableFactory)));
    }

    private SpecConfigurableRule createModuleSpecConfigurableRule(Object id, ConfigurableRule<ComponentMetadataContext> instantiatingAction) {
        ModuleIdentifier moduleIdentifier;

        try {
            moduleIdentifier = moduleIdentifierNotationParser.parseNotation(id);
        } catch (UnsupportedNotationException e) {
            throw new InvalidUserCodeException(String.format(INVALID_SPEC_ERROR, id == null ? "null" : id.toString()), e);
        }

        Spec<ModuleVersionIdentifier> spec = new ModuleVersionIdentifierSpec(moduleIdentifier);
        return new SpecConfigurableRule(instantiatingAction, spec);
    }

    private SpecConfigurableRule createAllSpecConfigurableRule(ConfigurableRule<ComponentMetadataContext> instantiatingAction) {
        return new SpecConfigurableRule(instantiatingAction, Specs.<ModuleVersionIdentifier>satisfyAll());
    }

    public ModuleComponentResolveMetadata processMetadata(ModuleComponentResolveMetadata metadata, CachePolicy cachePolicy) {
        ModuleComponentResolveMetadata updatedMetadata;
        if (rules.isEmpty() && classBasedRules.isEmpty()) {
            updatedMetadata = metadata;
        } else if (rules.isEmpty()) {
            updatedMetadata = processClassRuleWithCaching(metadata, cachePolicy);
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
        processClassRule(metadata, details, id);
    }

    private void processClassRule(final ModuleComponentResolveMetadata metadata, final ComponentMetadataDetails details, ModuleVersionIdentifier id) {
        InstantiatingAction<ComponentMetadataContext> action = collectRulesAndCreateAction(id);

        DefaultComponentMetadataContext componentMetadataContext = new DefaultComponentMetadataContext(details, metadata);
        try {
            synchronized (this) {
                action.execute(componentMetadataContext);
            }
        } catch (InvalidUserCodeException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidUserCodeException(String.format("There was an error while evaluating a component metadata rule for %s.", details.getId()), e);
        }
    }

    private ModuleComponentResolveMetadata processClassRuleWithCaching(final ModuleComponentResolveMetadata metadata, CachePolicy cachePolicy) {
        InstantiatingAction<ComponentMetadataContext> action = collectRulesAndCreateAction(metadata.getModuleVersionId());
        try {
            synchronized (this) {
                return ruleExecutor.execute(metadata, action, new Transformer<ModuleComponentResolveMetadata, WrappingComponentMetadataContext>() {
                        @Override
                        public ModuleComponentResolveMetadata transform(WrappingComponentMetadataContext componentMetadataContext) {
                            return componentMetadataContext.getMutableMetadata().asImmutable();
                        }
                    },
                    new Transformer<WrappingComponentMetadataContext, ModuleComponentResolveMetadata>() {
                        @Override
                        public WrappingComponentMetadataContext transform(ModuleComponentResolveMetadata moduleVersionIdentifier) {
                            return new WrappingComponentMetadataContext(metadata, instantiator, dependencyMetadataNotationParser, dependencyConstraintMetadataNotationParser);
                        }
                    }, cachePolicy);
            }
        } catch (InvalidUserCodeException e) {
            throw e;
        } catch (Exception e) {
            throw new InvalidUserCodeException(String.format("There was an error while evaluating a component metadata rule for %s.", metadata.getModuleVersionId()), e);
        }
    }

    private InstantiatingAction<ComponentMetadataContext> collectRulesAndCreateAction(ModuleVersionIdentifier id) {
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

    private List<Class<?>> getParamsList(Class<? extends ComponentMetadataRule> rule) {
        return Collections.emptyList();
    }

    static class ComponentMetadataDetailsMatchingSpec implements Spec<ComponentMetadataDetails> {
        private ModuleIdentifier target;

        ComponentMetadataDetailsMatchingSpec(ModuleIdentifier target) {
            this.target = target;
        }

        public boolean isSatisfiedBy(ComponentMetadataDetails componentMetadataDetails) {
            ModuleVersionIdentifier identifier = componentMetadataDetails.getId();
            return identifier.getGroup().equals(target.getGroup()) && identifier.getName().equals(target.getName());
        }
    }

    static class ModuleVersionIdentifierSpec implements Spec<ModuleVersionIdentifier> {
        private ModuleIdentifier target;

        ModuleVersionIdentifierSpec(ModuleIdentifier target) {
            this.target = target;
        }

        public boolean isSatisfiedBy(ModuleVersionIdentifier identifier) {
            return identifier.getGroup().equals(target.getGroup()) && identifier.getName().equals(target.getName());
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
