/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.dependencysubstitution;

import org.gradle.api.Action;
import org.gradle.api.artifacts.ArtifactSelectionDetails;
import org.gradle.api.artifacts.DependencyResolveDetails;
import org.gradle.api.artifacts.DependencySubstitution;
import org.gradle.api.artifacts.DependencySubstitutions;
import org.gradle.api.artifacts.ModuleDependencyCapabilitiesHandler;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.VariantSelectionDetails;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentSelector;
import org.gradle.api.artifacts.result.ComponentSelectionDescriptor;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Category;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.internal.artifacts.ComponentSelectorConverter;
import org.gradle.api.internal.artifacts.DependencySubstitutionInternal;
import org.gradle.api.internal.artifacts.component.ComponentIdentifierFactory;
import org.gradle.api.internal.artifacts.configurations.MutationValidator;
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableModuleDependencyCapabilitiesHandler;
import org.gradle.api.internal.artifacts.dependencies.ModuleDependencyCapabilitiesInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons;
import org.gradle.api.internal.attributes.AttributeContainerInternal;
import org.gradle.api.internal.attributes.ImmutableAttributesFactory;
import org.gradle.api.model.ObjectFactory;
import org.gradle.internal.Actions;
import org.gradle.internal.Describables;
import org.gradle.internal.ImmutableActionSet;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector;
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector;
import org.gradle.internal.exceptions.DiagnosticsVisitor;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.typeconversion.NotationConvertResult;
import org.gradle.internal.typeconversion.NotationConverter;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.internal.typeconversion.NotationParserBuilder;
import org.gradle.internal.typeconversion.TypeConversionException;
import org.gradle.util.Path;

import javax.inject.Inject;
import java.util.List;
import java.util.function.Supplier;

public class DefaultDependencySubstitutions implements DependencySubstitutionsInternal {
    private final NotationParser<Object, ComponentSelector> moduleSelectorNotationParser;
    private final NotationParser<Object, ComponentSelector> projectSelectorNotationParser;
    private final ComponentSelectionDescriptor reason;
    private final Instantiator instantiator;
    private final ObjectFactory objectFactory;
    private final ImmutableAttributesFactory attributesFactory;
    private final NotationParser<Object, Capability> capabilityNotationParser;

    private MutationValidator mutationValidator = MutationValidator.IGNORE;
    private ImmutableActionSet<DependencySubstitution> substitutionRules;
    private boolean rulesMayAddProjectDependency;

    public static DefaultDependencySubstitutions forResolutionStrategy(ComponentIdentifierFactory componentIdentifierFactory,
                                                                       NotationParser<Object, ComponentSelector> moduleSelectorNotationParser,
                                                                       Instantiator instantiator,
                                                                       ObjectFactory objectFactory,
                                                                       ImmutableAttributesFactory attributesFactory,
                                                                       NotationParser<Object, Capability> capabilityNotationParser) {
        NotationParser<Object, ComponentSelector> projectSelectorNotationParser = NotationParserBuilder
            .toType(ComponentSelector.class)
            .fromCharSequence(new ProjectPathConverter(componentIdentifierFactory))
            .toComposite();
        return instantiator.newInstance(DefaultDependencySubstitutions.class,
            ComponentSelectionReasons.SELECTED_BY_RULE,
            projectSelectorNotationParser,
            moduleSelectorNotationParser,
            instantiator,
            objectFactory,
            attributesFactory,
            capabilityNotationParser);
    }

    public static DefaultDependencySubstitutions forIncludedBuild(IncludedBuildState build,
                                                                  Instantiator instantiator,
                                                                  ObjectFactory objectFactory,
                                                                  ImmutableAttributesFactory attributesFactory,
                                                                  NotationParser<Object, ComponentSelector> moduleSelectorNotationParser,
                                                                  NotationParser<Object, Capability> capabilityNotationParser) {
        NotationParser<Object, ComponentSelector> projectSelectorNotationParser = NotationParserBuilder
            .toType(ComponentSelector.class)
            .fromCharSequence(new CompositeProjectPathConverter(build))
            .toComposite();
        return instantiator.newInstance(CompositeBuildAwareSubstitutions.class, projectSelectorNotationParser, moduleSelectorNotationParser, instantiator, objectFactory, attributesFactory, capabilityNotationParser, build);
    }

    @Inject
    public DefaultDependencySubstitutions(ComponentSelectionDescriptor reason,
                                          NotationParser<Object, ComponentSelector> projectSelectorNotationParser,
                                          NotationParser<Object, ComponentSelector> moduleSelectorNotationParser,
                                          Instantiator instantiator,
                                          ObjectFactory objectFactory,
                                          ImmutableAttributesFactory attributesFactory,
                                          NotationParser<Object, Capability> capabilityNotationParser) {
        this(reason, ImmutableActionSet.empty(), moduleSelectorNotationParser, projectSelectorNotationParser, instantiator, objectFactory, attributesFactory, capabilityNotationParser);
    }

    private DefaultDependencySubstitutions(ComponentSelectionDescriptor reason,
                                           ImmutableActionSet<DependencySubstitution> substitutionRules,
                                           NotationParser<Object, ComponentSelector> moduleSelectorNotationParser,
                                           NotationParser<Object, ComponentSelector> projectSelectorNotationParser,
                                           Instantiator instantiator,
                                           ObjectFactory objectFactory,
                                           ImmutableAttributesFactory attributesFactory,
                                           NotationParser<Object, Capability> capabilityNotationParser) {
        this.reason = reason;
        this.substitutionRules = substitutionRules;
        this.moduleSelectorNotationParser = moduleSelectorNotationParser;
        this.projectSelectorNotationParser = projectSelectorNotationParser;
        this.instantiator = instantiator;
        this.objectFactory = objectFactory;
        this.attributesFactory = attributesFactory;
        this.capabilityNotationParser = capabilityNotationParser;
    }

    @Override
    public void discard() {
        substitutionRules = ImmutableActionSet.empty();
        rulesMayAddProjectDependency = false;
    }

    @Override
    public boolean rulesMayAddProjectDependency() {
        return rulesMayAddProjectDependency;
    }

    @Override
    public Action<DependencySubstitution> getRuleAction() {
        return substitutionRules;
    }

    protected void addSubstitution(Action<? super DependencySubstitution> rule, boolean projectInvolved) {
        addRule(rule);
        if (projectInvolved) {
            rulesMayAddProjectDependency = true;
        }
    }

    private void addRule(Action<? super DependencySubstitution> rule) {
        mutationValidator.validateMutation(MutationValidator.MutationType.STRATEGY);
        substitutionRules = substitutionRules.add(rule);
    }

    @Override
    public DependencySubstitutions all(Action<? super DependencySubstitution> rule) {
        addRule(rule);
        rulesMayAddProjectDependency = true;
        return this;
    }

    @Override
    public DependencySubstitutions allWithDependencyResolveDetails(Action<? super DependencyResolveDetails> rule, ComponentSelectorConverter componentSelectorConverter) {
        addRule(new DependencyResolveDetailsWrapperAction(rule, componentSelectorConverter, Actions::doNothing, instantiator));
        return this;
    }

    @Override
    public ComponentSelector module(String notation) {
        return moduleSelectorNotationParser.parseNotation(notation);
    }

    @Override
    public ComponentSelector project(final String path) {
        return projectSelectorNotationParser.parseNotation(path);
    }

    @Override
    public ComponentSelector platform(ComponentSelector selector) {
        return variant(selector, VariantSelectionDetails::platform);
    }

    @Override
    public ComponentSelector variant(ComponentSelector selector, Action<? super VariantSelectionDetails> detailsAction) {
        DefaultVariantSelectionDetails details = instantiator.newInstance(DefaultVariantSelectionDetails.class,
            attributesFactory,
            objectFactory,
            capabilityNotationParser,
            instantiator,
            selector);
        detailsAction.execute(details);
        return details.selector;
    }

    @Override
    public Substitution substitute(final ComponentSelector substituted) {
        return new Substitution() {
            Action<? super ArtifactSelectionDetails> artifactAction = Actions.doNothing();

            ComponentSelectionDescriptorInternal substitutionReason = (ComponentSelectionDescriptorInternal) reason;

            @Override
            public Substitution because(String description) {
                substitutionReason = substitutionReason.withDescription(Describables.of(description));
                return this;
            }

            @Override
            public Substitution withClassifier(String classifier) {
                artifactAction = Actions.composite(artifactAction, new SetClassifier(classifier));
                return this;
            }

            @Override
            public Substitution withoutClassifier() {
                artifactAction = Actions.composite(artifactAction, NoClassifier.INSTANCE);
                return this;
            }

            @Override
            public Substitution withoutArtifactSelectors() {
                artifactAction = Actions.composite(artifactAction, NoArtifactSelector.INSTANCE);
                return this;
            }

            @Override
            public Substitution using(ComponentSelector notation) {
                DefaultDependencySubstitution.validateTarget(notation);

                // A project is involved, need to be aware of it
                boolean projectInvolved = substituted instanceof ProjectComponentSelector || notation instanceof ProjectComponentSelector;

                if (substituted instanceof UnversionedModuleComponentSelector) {
                    final ModuleIdentifier moduleId = ((UnversionedModuleComponentSelector) substituted).getModuleIdentifier();
                    if (notation instanceof ModuleComponentSelector) {
                        if (((ModuleComponentSelector) notation).getModuleIdentifier().equals(moduleId)) {
                            // This substitution is effectively a force
                            substitutionReason = substitutionReason.markAsEquivalentToForce();
                        }
                    }
                    addSubstitution(new ModuleMatchDependencySubstitutionAction(substitutionReason, moduleId, notation, () -> artifactAction), projectInvolved);
                } else {
                    addSubstitution(new ExactMatchDependencySubstitutionAction(substitutionReason, substituted, notation, () -> artifactAction), projectInvolved);
                }
                return this;
            }
        };
    }

    @Override
    public void setMutationValidator(MutationValidator validator) {
        mutationValidator = validator;
    }

    @Override
    public DependencySubstitutionsInternal copy() {
        return new DefaultDependencySubstitutions(
            reason,
            substitutionRules,
            moduleSelectorNotationParser,
            projectSelectorNotationParser,
            instantiator,
            objectFactory,
            attributesFactory,
            capabilityNotationParser);
    }

    private static class ProjectPathConverter implements NotationConverter<String, ProjectComponentSelector> {
        private final ComponentIdentifierFactory componentIdentifierFactory;

        private ProjectPathConverter(ComponentIdentifierFactory componentIdentifierFactory) {
            this.componentIdentifierFactory = componentIdentifierFactory;
        }

        @Override
        public void describe(DiagnosticsVisitor visitor) {
            visitor.example("Project paths, e.g. ':api'.");
        }

        @Override
        public void convert(String notation, NotationConvertResult<? super ProjectComponentSelector> result) throws TypeConversionException {
            result.converted(componentIdentifierFactory.createProjectComponentSelector(notation));
        }
    }

    private static class CompositeProjectPathConverter implements NotationConverter<String, ProjectComponentSelector> {
        private final IncludedBuildState build;

        private CompositeProjectPathConverter(IncludedBuildState build) {
            this.build = build;
        }

        @Override
        public void describe(DiagnosticsVisitor visitor) {
            visitor.example("Project paths, e.g. ':api'.");
        }

        @Override
        public void convert(String notation, NotationConvertResult<? super ProjectComponentSelector> result) throws TypeConversionException {
            result.converted(DefaultProjectComponentSelector.newSelector(identifierForProject(build, notation)));
        }

        static ProjectComponentIdentifier identifierForProject(IncludedBuildState build, String notation) {
            return build.getProjects().getProject(Path.path(notation)).getComponentIdentifier();
        }
    }

    private static class CompositeBuildSubstitutionAction implements Action<DependencySubstitution> {
        private final Action<? super DependencySubstitution> delegate;
        private final IncludedBuildState build;

        private CompositeBuildSubstitutionAction(Action<? super DependencySubstitution> delegate, IncludedBuildState build) {
            this.delegate = delegate;
            this.build = build;
        }

        @Override
        public void execute(DependencySubstitution dependencySubstitution) {
            DependencySubstitutionInternal ds = (DependencySubstitutionInternal) dependencySubstitution;
            delegate.execute(new DependencySubstitutionInternal() {
                @Override
                public ComponentSelector getTarget() {
                    return ds.getTarget();
                }

                @Override
                public List<ComponentSelectionDescriptorInternal> getRuleDescriptors() {
                    return ds.getRuleDescriptors();
                }

                @Override
                public boolean isUpdated() {
                    return ds.isUpdated();
                }

                @Override
                public ArtifactSelectionDetailsInternal getArtifactSelectionDetails() {
                    return ds.getArtifactSelectionDetails();
                }

                @Override
                public ComponentSelector getRequested() {
                    return ds.getRequested();
                }

                // Implicitly set the substituted dependency attributes as the target dependency attributes
                private Object addImplicitRequestAttributesAndCapabilities(Object notation) {
                    if (notation instanceof ProjectComponentSelector) {
                        ProjectComponentIdentifier id = CompositeProjectPathConverter.identifierForProject(build, ((ProjectComponentSelector) notation).getProjectPath());
                        ComponentSelector requested = getRequested();
                        return DefaultProjectComponentSelector.newSelector(
                            id,
                            ((AttributeContainerInternal) requested.getAttributes()).asImmutable(),
                            requested.getRequestedCapabilities()
                        );
                    }
                    return notation;
                }

                @Override
                public void useTarget(Object notation, ComponentSelectionDescriptor ruleDescriptor) {
                    ds.useTarget(addImplicitRequestAttributesAndCapabilities(notation), ruleDescriptor);
                }

                @Override
                public void useTarget(Object notation) {
                    ds.useTarget(addImplicitRequestAttributesAndCapabilities(notation));
                }

                @Override
                public void useTarget(Object notation, String reason) {
                    ds.useTarget(addImplicitRequestAttributesAndCapabilities(notation), reason);
                }

                @Override
                public void artifactSelection(Action<? super ArtifactSelectionDetails> action) {
                    ds.artifactSelection(action);
                }
            });
        }
    }

    private abstract static class AbstractDependencySubstitutionAction implements Action<DependencySubstitution> {
        private final Supplier<Action<? super ArtifactSelectionDetails>> artifactSelectionAction;

        protected AbstractDependencySubstitutionAction(Supplier<Action<? super ArtifactSelectionDetails>> artifactSelectionAction) {
            this.artifactSelectionAction = artifactSelectionAction;
        }

        @Override
        public void execute(DependencySubstitution dependencySubstitution) {
            dependencySubstitution.artifactSelection(artifactSelectionAction.get());
        }
    }

    private static class ExactMatchDependencySubstitutionAction extends AbstractDependencySubstitutionAction {
        private final ComponentSelectionDescriptorInternal selectionReason;
        private final ComponentSelector substituted;
        private final ComponentSelector substitute;

        public ExactMatchDependencySubstitutionAction(ComponentSelectionDescriptorInternal selectionReason, ComponentSelector substituted, ComponentSelector substitute, Supplier<Action<? super ArtifactSelectionDetails>> artifactSelectionAction) {
            super(artifactSelectionAction);
            this.selectionReason = selectionReason;
            this.substituted = substituted;
            this.substitute = substitute;
        }

        @Override
        public void execute(DependencySubstitution dependencySubstitution) {
            if (substituted.equals(dependencySubstitution.getRequested())) {
                super.execute(dependencySubstitution);
                ((DependencySubstitutionInternal) dependencySubstitution).useTarget(substitute, selectionReason);
            }
        }

    }

    private static class ModuleMatchDependencySubstitutionAction extends AbstractDependencySubstitutionAction {
        private final ComponentSelectionDescriptorInternal selectionReason;
        private final ModuleIdentifier moduleId;
        private final ComponentSelector substitute;

        public ModuleMatchDependencySubstitutionAction(ComponentSelectionDescriptorInternal selectionReason, ModuleIdentifier moduleId, ComponentSelector substitute, Supplier<Action<? super ArtifactSelectionDetails>> artifactSelectionAction) {
            super(artifactSelectionAction);
            this.selectionReason = selectionReason;
            this.moduleId = moduleId;
            this.substitute = substitute;
        }

        @Override
        public void execute(DependencySubstitution dependencySubstitution) {
            if (dependencySubstitution.getRequested() instanceof ModuleComponentSelector) {
                ModuleComponentSelector requested = (ModuleComponentSelector) dependencySubstitution.getRequested();
                if (moduleId.equals(requested.getModuleIdentifier())) {
                    super.execute(dependencySubstitution);
                    ((DependencySubstitutionInternal) dependencySubstitution).useTarget(substitute, selectionReason);
                }
            }
        }
    }

    private static class DependencyResolveDetailsWrapperAction extends AbstractDependencySubstitutionAction {
        private final Action<? super DependencyResolveDetails> delegate;
        private final ComponentSelectorConverter componentSelectorConverter;
        private final Instantiator instantiator;

        public DependencyResolveDetailsWrapperAction(Action<? super DependencyResolveDetails> delegate, ComponentSelectorConverter componentSelectorConverter, Supplier<Action<? super ArtifactSelectionDetails>> artifactSelectionAction, Instantiator instantiator) {
            super(artifactSelectionAction);
            this.delegate = delegate;
            this.componentSelectorConverter = componentSelectorConverter;
            this.instantiator = instantiator;
        }

        @Override
        public void execute(DependencySubstitution substitution) {
            super.execute(substitution);
            ModuleVersionSelector requested = componentSelectorConverter.getSelector(substitution.getRequested());
            DefaultDependencyResolveDetails details = instantiator.newInstance(DefaultDependencyResolveDetails.class, substitution, requested);
            delegate.execute(details);
            details.complete();
        }
    }

    private static class SetClassifier implements Action<ArtifactSelectionDetails> {
        private final String classifier;

        public SetClassifier(String classifier) {
            this.classifier = classifier;
        }

        @Override
        public void execute(ArtifactSelectionDetails artifactSelectionDetails) {
            artifactSelectionDetails.selectArtifact("jar", null, classifier);
        }
    }

    private static class NoClassifier implements Action<ArtifactSelectionDetails> {
        private static final NoClassifier INSTANCE = new NoClassifier();

        @Override
        public void execute(ArtifactSelectionDetails artifactSelectionDetails) {
            artifactSelectionDetails.selectArtifact("jar", null, null);
        }
    }

    private static class NoArtifactSelector implements Action<ArtifactSelectionDetails> {
        private static final NoArtifactSelector INSTANCE = new NoArtifactSelector();

        @Override
        public void execute(ArtifactSelectionDetails artifactSelectionDetails) {
            artifactSelectionDetails.withoutArtifactSelectors();
        }
    }

    public static class DefaultVariantSelectionDetails implements VariantSelectionDetails {
        private final ImmutableAttributesFactory attributesFactory;
        private final ObjectFactory objectFactory;
        private final NotationParser<Object, Capability> capabilityNotationParser;
        private final Instantiator instantatior;
        private ComponentSelector selector;

        @Inject
        public DefaultVariantSelectionDetails(ImmutableAttributesFactory attributesFactory,
                                              ObjectFactory objectFactory,
                                              NotationParser<Object, Capability> capabilityNotationParser,
                                              Instantiator instantatior,
                                              ComponentSelector selector) {
            this.attributesFactory = attributesFactory;
            this.objectFactory = objectFactory;
            this.capabilityNotationParser = capabilityNotationParser;
            this.instantatior = instantatior;
            this.selector = selector;
        }

        private void createComponentOfCategory(String category) {
            if (selector instanceof ProjectComponentSelector) {
                AttributeContainerInternal container = createCategory(category);
                selector = DefaultProjectComponentSelector.withAttributes((ProjectComponentSelector) selector, container.asImmutable());
            } else if (selector instanceof ModuleComponentSelector) {
                AttributeContainerInternal container = createCategory(category);
                selector = DefaultModuleComponentSelector.withAttributes((ModuleComponentSelector) selector, container.asImmutable());
            }
        }

        private AttributeContainerInternal createCategory(String category) {
            return (AttributeContainerInternal) attributesFactory.mutable()
                .attribute(Category.CATEGORY_ATTRIBUTE, objectFactory.named(Category.class, category));
        }

        @Override
        public void platform() {
            createComponentOfCategory(Category.REGULAR_PLATFORM);
        }

        @Override
        public void enforcedPlatform() {
            createComponentOfCategory(Category.ENFORCED_PLATFORM);
        }

        @Override
        public void library() {
            createComponentOfCategory(Category.LIBRARY);
        }

        @Override
        public void attributes(Action<? super AttributeContainer> configurationAction) {
            AttributeContainerInternal container = attributesFactory.mutable();
            configurationAction.execute(container);
            if (selector instanceof ProjectComponentSelector) {
                selector = DefaultProjectComponentSelector.withAttributes((ProjectComponentSelector) selector, container.asImmutable());
            } else if (selector instanceof ModuleComponentSelector) {
                selector = DefaultModuleComponentSelector.withAttributes((ModuleComponentSelector) selector, container.asImmutable());
            }
        }

        @Override
        public void capabilities(Action<? super ModuleDependencyCapabilitiesHandler> configurationAction) {
            ModuleDependencyCapabilitiesInternal handler = instantatior.newInstance(DefaultMutableModuleDependencyCapabilitiesHandler.class,
                capabilityNotationParser
            );
            configurationAction.execute(handler);
            if (selector instanceof ProjectComponentSelector) {
                selector = DefaultProjectComponentSelector.withCapabilities((ProjectComponentSelector) selector, handler.getRequestedCapabilities());
            } else if (selector instanceof ModuleComponentSelector) {
                selector = DefaultModuleComponentSelector.withCapabilities((ModuleComponentSelector) selector, handler.getRequestedCapabilities());
            }
        }
    }

    public static class CompositeBuildAwareSubstitutions extends DefaultDependencySubstitutions {
        private final IncludedBuildState build;

        @Inject
        public CompositeBuildAwareSubstitutions(NotationParser<Object, ComponentSelector> projectSelectorNotationParser, NotationParser<Object, ComponentSelector> moduleIdentifierFactory, Instantiator instantiator, ObjectFactory objectFactory, ImmutableAttributesFactory attributesFactory, NotationParser<Object, Capability> capabilityNotationParser, IncludedBuildState build) {
            super(ComponentSelectionReasons.COMPOSITE_BUILD, projectSelectorNotationParser, moduleIdentifierFactory, instantiator, objectFactory, attributesFactory, capabilityNotationParser);
            this.build = build;
        }

        @Override
        protected void addSubstitution(Action<? super DependencySubstitution> rule, boolean projectInvolved) {
            CompositeBuildSubstitutionAction decorated = new CompositeBuildSubstitutionAction(rule, build);
            super.addSubstitution(decorated, projectInvolved);
        }
    }
}
