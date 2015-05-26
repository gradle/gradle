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
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.component.ProjectComponentSelector;
import org.gradle.api.internal.artifacts.DependencySubstitutionInternal;
import org.gradle.api.internal.artifacts.configurations.MutationValidator;
import org.gradle.internal.Actions;
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector;
import org.gradle.internal.exceptions.DiagnosticsVisitor;
import org.gradle.internal.typeconversion.*;

import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultDependencySubstitutions implements DependencySubstitutionsInternal {
    private final Set<Action<? super DependencySubstitution>> substitutionRules;
    private final NotationParser<Object, ComponentSelector> moduleSelectorNotationParser;
    private final NotationParser<Object, ComponentSelector> projectSelectorNotationParser;

    private MutationValidator mutationValidator = MutationValidator.IGNORE;
    private boolean hasDependencySubstitutionRule;

    public DefaultDependencySubstitutions() {
        this(new LinkedHashSet<Action<? super DependencySubstitution>>());
    }

    DefaultDependencySubstitutions(Set<Action<? super DependencySubstitution>> substitutionRules) {
        this.substitutionRules = substitutionRules;
        this.moduleSelectorNotationParser = createModuleSelectorNotationParser();
        this.projectSelectorNotationParser = createProjectSelectorNotationParser();
    }

    @Override
    public boolean hasDependencySubstitutionRules() {
        return hasDependencySubstitutionRule;
    }

    @Override
    public Action<DependencySubstitution> getDependencySubstitutionRule() {
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
    public DependencySubstitutions allWithDependencyResolveDetails(Action<? super DependencyResolveDetails> rule) {
        addRule(new DependencyResolveDetailsWrapperAction(rule));
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
        return new DefaultDependencySubstitutions(new LinkedHashSet<Action<? super DependencySubstitution>>(substitutionRules));
    }

    private static NotationParser<Object, ComponentSelector> createModuleSelectorNotationParser() {
        return NotationParserBuilder
                .toType(ComponentSelector.class)
                .converter(new ModuleSelectorStringNotationConverter())
                .toComposite();
    }

    private static NotationParser<Object, ComponentSelector> createProjectSelectorNotationParser() {
        return NotationParserBuilder
                .toType(ComponentSelector.class)
                .fromCharSequence(new ProjectPathConverter())
                .toComposite();
    }

    private static class ProjectPathConverter implements NotationConverter<String, ProjectComponentSelector> {
        @Override
        public void describe(DiagnosticsVisitor visitor) {
            visitor.example("Project paths, e.g. ':api'.");
        }

        @Override
        public void convert(String notation, NotationConvertResult<? super ProjectComponentSelector> result) throws TypeConversionException {
            result.converted(DefaultProjectComponentSelector.newSelector(notation));
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
                dependencySubstitution.useTarget(substitute);
            }
        }
    }

    private static class ModuleMatchDependencySubstitutionAction implements Action<DependencySubstitution> {
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
                    dependencySubstitution.useTarget(substitute);
                }
            }
        }
    }

    private static class DependencyResolveDetailsWrapperAction implements Action<DependencySubstitution> {
        private final Action<? super DependencyResolveDetails> delegate;

        public DependencyResolveDetailsWrapperAction(Action<? super DependencyResolveDetails> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void execute(DependencySubstitution substitution) {
            DefaultDependencyResolveDetails details = new DefaultDependencyResolveDetails((DependencySubstitutionInternal) substitution);
            delegate.execute(details);
        }
    }
}
