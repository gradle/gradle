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
import org.gradle.api.artifacts.DependencyResolveDetails;
import org.gradle.api.artifacts.DependencySubstitution;
import org.gradle.api.artifacts.DependencySubstitutions;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionSelector;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.component.ProjectComponentSelector;
import org.gradle.api.artifacts.result.ComponentSelectionReason;
import org.gradle.api.initialization.IncludedBuild;
import org.gradle.api.internal.artifacts.ComponentSelectorConverter;
import org.gradle.api.internal.artifacts.DependencySubstitutionInternal;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.component.ComponentIdentifierFactory;
import org.gradle.api.internal.artifacts.configurations.MutationValidator;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.VersionSelectionReasons;
import org.gradle.internal.Actions;
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector;
import org.gradle.internal.exceptions.DiagnosticsVisitor;
import org.gradle.internal.typeconversion.NotationConvertResult;
import org.gradle.internal.typeconversion.NotationConverter;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.internal.typeconversion.NotationParserBuilder;
import org.gradle.internal.typeconversion.TypeConversionException;

import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultDependencySubstitutions implements DependencySubstitutionsInternal {
    private final Set<Action<? super DependencySubstitution>> substitutionRules;
    private final NotationParser<Object, ComponentSelector> moduleSelectorNotationParser;
    private final NotationParser<Object, ComponentSelector> projectSelectorNotationParser;
    private final ComponentSelectionReason reason;

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
        return new DefaultDependencySubstitutions(VersionSelectionReasons.SELECTED_BY_RULE, projectSelectorNotationParser, moduleIdentifierFactory);
    }

    public static DefaultDependencySubstitutions forIncludedBuild(IncludedBuild build, ImmutableModuleIdentifierFactory moduleIdentifierFactory) {
        NotationParser<Object, ComponentSelector> projectSelectorNotationParser = NotationParserBuilder
                .toType(ComponentSelector.class)
                .fromCharSequence(new CompositeProjectPathConverter(build))
                .toComposite();
        return new DefaultDependencySubstitutions(VersionSelectionReasons.COMPOSITE_BUILD, projectSelectorNotationParser, moduleIdentifierFactory);
    }

    private DefaultDependencySubstitutions(ComponentSelectionReason reason, NotationParser<Object, ComponentSelector> projectSelectorNotationParser, ImmutableModuleIdentifierFactory moduleIdentifierFactory) {
        this(reason, new LinkedHashSet<Action<? super DependencySubstitution>>(), moduleSelectorNotationConverter(moduleIdentifierFactory), projectSelectorNotationParser);
    }

    private DefaultDependencySubstitutions(ComponentSelectionReason reason,
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
        addRule(new DependencyResolveDetailsWrapperAction(rule, componentSelectorConverter));
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
            @Override
            public void with(ComponentSelector substitute) {
                DefaultDependencySubstitution.validateTarget(substitute);

                if (substituted instanceof UnversionedModuleComponentSelector) {
                    final ModuleIdentifier moduleId = ((UnversionedModuleComponentSelector) substituted).getModuleIdentifier();
                    all(new ModuleMatchDependencySubstitutionAction(moduleId, substitute));
                } else {
                    all(new ExactMatchDependencySubstitutionAction(substituted, substitute));
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
            new LinkedHashSet<Action<? super DependencySubstitution>>(substitutionRules),
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
        private final IncludedBuild build;

        private CompositeProjectPathConverter(IncludedBuild build) {
            this.build = build;
        }

        @Override
        public void describe(DiagnosticsVisitor visitor) {
            visitor.example("Project paths, e.g. ':api'.");
        }

        @Override
        public void convert(String notation, NotationConvertResult<? super ProjectComponentSelector> result) throws TypeConversionException {
            result.converted(DefaultProjectComponentSelector.newSelector(build, notation));
        }
    }

    private class ExactMatchDependencySubstitutionAction implements Action<DependencySubstitution> {
        private final ComponentSelector substituted;
        private final ComponentSelector substitute;

        public ExactMatchDependencySubstitutionAction(ComponentSelector substituted, ComponentSelector substitute) {
            this.substituted = substituted;
            this.substitute = substitute;
        }

        @Override
        public void execute(DependencySubstitution dependencySubstitution) {
            if (substituted.equals(dependencySubstitution.getRequested())) {
                ((DependencySubstitutionInternal) dependencySubstitution).useTarget(substitute, reason);
            }
        }
    }

    private class ModuleMatchDependencySubstitutionAction implements Action<DependencySubstitution> {
        private final ModuleIdentifier moduleId;
        private final ComponentSelector substitute;

        public ModuleMatchDependencySubstitutionAction(ModuleIdentifier moduleId, ComponentSelector substitute) {
            this.moduleId = moduleId;
            this.substitute = substitute;
        }

        @Override
        public void execute(DependencySubstitution dependencySubstitution) {
            if (dependencySubstitution.getRequested() instanceof ModuleComponentSelector) {
                ModuleComponentSelector requested = (ModuleComponentSelector) dependencySubstitution.getRequested();
                if (moduleId.getGroup().equals(requested.getGroup()) && moduleId.getName().equals(requested.getModule())) {
                    ((DependencySubstitutionInternal) dependencySubstitution).useTarget(substitute, reason);
                }
            }
        }
    }

    private static class DependencyResolveDetailsWrapperAction implements Action<DependencySubstitution> {
        private final Action<? super DependencyResolveDetails> delegate;
        private final ComponentSelectorConverter componentSelectorConverter;

        public DependencyResolveDetailsWrapperAction(Action<? super DependencyResolveDetails> delegate, ComponentSelectorConverter componentSelectorConverter) {
            this.delegate = delegate;
            this.componentSelectorConverter = componentSelectorConverter;
        }

        @Override
        public void execute(DependencySubstitution substitution) {
            ModuleVersionSelector requested = componentSelectorConverter.getSelector(substitution.getRequested());
            DefaultDependencyResolveDetails details = new DefaultDependencyResolveDetails((DependencySubstitutionInternal) substitution, requested);
            delegate.execute(details);
        }
    }
}
