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
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.component.ProjectComponentSelector;
import org.gradle.api.artifacts.result.ComponentSelectionDescriptor;
import org.gradle.api.internal.artifacts.ComponentSelectorConverter;
import org.gradle.api.internal.artifacts.DependencySubstitutionInternal;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.component.ComponentIdentifierFactory;
import org.gradle.api.internal.artifacts.configurations.MutationValidator;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons;
import org.gradle.internal.Actions;
import org.gradle.internal.Describables;
import org.gradle.internal.build.IncludedBuildState;
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector;
import org.gradle.internal.exceptions.DiagnosticsVisitor;
import org.gradle.internal.typeconversion.NotationConvertResult;
import org.gradle.internal.typeconversion.NotationConverter;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.internal.typeconversion.NotationParserBuilder;
import org.gradle.internal.typeconversion.TypeConversionException;
import org.gradle.util.Path;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;

public class DefaultDependencySubstitutions implements DependencySubstitutionsInternal {
    private final Set<Action<? super DependencySubstitution>> substitutionRules;
    private final NotationParser<Object, ComponentSelector> moduleSelectorNotationParser;
    private final NotationParser<Object, ComponentSelector> projectSelectorNotationParser;
    private final ComponentSelectionDescriptor reason;

    private MutationValidator mutationValidator = MutationValidator.IGNORE;
    private boolean hasDependencySubstitutionRule;

    private static NotationParser<Object, ComponentSelector> moduleSelectorNotationConverter(ImmutableModuleIdentifierFactory moduleIdentifierFactory) {
        return NotationParserBuilder
            .toType(ComponentSelector.class)
            .converter(new ModuleSelectorStringNotationConverter(moduleIdentifierFactory))
            .toComposite();
    }

    public static DefaultDependencySubstitutions forResolutionStrategy(ComponentIdentifierFactory componentIdentifierFactory, ImmutableModuleIdentifierFactory moduleIdentifierFactory) {
        NotationParser<Object, ComponentSelector> projectSelectorNotationParser = NotationParserBuilder
            .toType(ComponentSelector.class)
            .fromCharSequence(new ProjectPathConverter(componentIdentifierFactory))
            .toComposite();
        return new DefaultDependencySubstitutions(ComponentSelectionReasons.SELECTED_BY_RULE, projectSelectorNotationParser, moduleIdentifierFactory);
    }

    public static DefaultDependencySubstitutions forIncludedBuild(IncludedBuildState build, ImmutableModuleIdentifierFactory moduleIdentifierFactory) {
        NotationParser<Object, ComponentSelector> projectSelectorNotationParser = NotationParserBuilder
                .toType(ComponentSelector.class)
                .fromCharSequence(new CompositeProjectPathConverter(build))
                .toComposite();
        return new DefaultDependencySubstitutions(ComponentSelectionReasons.COMPOSITE_BUILD, projectSelectorNotationParser, moduleIdentifierFactory);
    }

    private DefaultDependencySubstitutions(ComponentSelectionDescriptor reason, NotationParser<Object, ComponentSelector> projectSelectorNotationParser, ImmutableModuleIdentifierFactory moduleIdentifierFactory) {
        this(reason, new LinkedHashSet<>(), moduleSelectorNotationConverter(moduleIdentifierFactory), projectSelectorNotationParser);
    }

    private DefaultDependencySubstitutions(ComponentSelectionDescriptor reason,
                                           Set<Action<? super DependencySubstitution>> substitutionRules,
                                           NotationParser<Object, ComponentSelector> moduleSelectorNotationParser,
                                           NotationParser<Object, ComponentSelector> projectSelectorNotationParser) {
        this.reason = reason;
        this.substitutionRules = substitutionRules;
        this.moduleSelectorNotationParser = moduleSelectorNotationParser;
        this.projectSelectorNotationParser = projectSelectorNotationParser;
    }

    @Override
    public boolean hasRules() {
        return hasDependencySubstitutionRule;
    }

    @Override
    public Action<DependencySubstitution> getRuleAction() {
        return Actions.composite(substitutionRules);
    }

    private void addSubstitution(Action<? super DependencySubstitution> rule, boolean projectInvolved) {
        addRule(rule);
        if (projectInvolved) {
            hasDependencySubstitutionRule = true;
        }
    }

    private void addRule(Action<? super DependencySubstitution> rule) {
        mutationValidator.validateMutation(MutationValidator.MutationType.STRATEGY);
        substitutionRules.add(rule);
    }

    @Override
    public DependencySubstitutions all(Action<? super DependencySubstitution> rule) {
        addRule(rule);
        hasDependencySubstitutionRule = true;
        return this;
    }

    @Override
    public DependencySubstitutions allWithDependencyResolveDetails(Action<? super DependencyResolveDetails> rule, ComponentSelectorConverter componentSelectorConverter) {
        addRule(new DependencyResolveDetailsWrapperAction(rule, componentSelectorConverter, Actions::doNothing));
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
            public Substitution usingClassifier(String classifier) {
                artifactAction = Actions.composite(artifactAction, new SetClassifier(classifier));
                return this;
            }

            @Override
            public Substitution withoutClassifier() {
                artifactAction = Actions.composite(artifactAction, NoClassifier.INSTANCE);
                return this;
            }

            @Override
            public Substitution to(ComponentSelector notation) {
                with(notation);
                return this;
            }

            @Override
            public void with(ComponentSelector substitute) {
                DefaultDependencySubstitution.validateTarget(substitute);

                boolean projectInvolved = false;
                if (substituted instanceof ProjectComponentSelector || substitute instanceof ProjectComponentSelector) {
                    // A project is involved, need to be aware of it
                    projectInvolved = true;
                }

                if (substituted instanceof UnversionedModuleComponentSelector) {
                    final ModuleIdentifier moduleId = ((UnversionedModuleComponentSelector) substituted).getModuleIdentifier();
                    if (substitute instanceof ModuleComponentSelector) {
                        if (((ModuleComponentSelector) substitute).getModuleIdentifier().equals(moduleId)) {
                            // This substitution is effectively a force
                            substitutionReason = substitutionReason.markAsEquivalentToForce();
                        }
                    }
                    addSubstitution(new ModuleMatchDependencySubstitutionAction(substitutionReason, moduleId, substitute, () -> artifactAction), projectInvolved);
                } else {
                    addSubstitution(new ExactMatchDependencySubstitutionAction(substitutionReason, substituted, substitute, () -> artifactAction), projectInvolved);
                }
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
            new LinkedHashSet<>(substitutionRules),
            moduleSelectorNotationParser,
            projectSelectorNotationParser);
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
            result.converted(DefaultProjectComponentSelector.newSelector(build.getIdentifierForProject(Path.path(notation))));
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

        public DependencyResolveDetailsWrapperAction(Action<? super DependencyResolveDetails> delegate, ComponentSelectorConverter componentSelectorConverter, Supplier<Action<? super ArtifactSelectionDetails>> artifactSelectionAction) {
            super(artifactSelectionAction);
            this.delegate = delegate;
            this.componentSelectorConverter = componentSelectorConverter;
        }

        @Override
        public void execute(DependencySubstitution substitution) {
            super.execute(substitution);
            ModuleVersionSelector requested = componentSelectorConverter.getSelector(substitution.getRequested());
            DefaultDependencyResolveDetails details = new DefaultDependencyResolveDetails((DependencySubstitutionInternal) substitution, requested);
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
}
