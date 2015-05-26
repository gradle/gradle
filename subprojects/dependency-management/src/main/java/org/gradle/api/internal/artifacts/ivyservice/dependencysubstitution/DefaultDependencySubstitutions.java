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
import org.gradle.api.Project;
import org.gradle.api.artifacts.DependencyResolveDetails;
import org.gradle.api.artifacts.DependencySubstitution;
import org.gradle.api.artifacts.DependencySubstitutions;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.DependencySubstitutionInternal;
import org.gradle.api.internal.artifacts.configurations.MutationValidator;
import org.gradle.api.internal.artifacts.dsl.ComponentSelectorParsers;
import org.gradle.api.internal.notations.ModuleIdentifierNotationConverter;
import org.gradle.internal.Actions;
import org.gradle.internal.component.local.model.DefaultProjectComponentIdentifier;
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector;
import org.gradle.internal.exceptions.DiagnosticsVisitor;
import org.gradle.internal.typeconversion.*;

import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultDependencySubstitutions implements DependencySubstitutionsInternal {
    private final Set<Action<? super DependencySubstitution>> substitutionRules;
    private final NotationParser<Object, ModuleIdentifier> moduleIdentifierNotationParser;
    private final NotationParser<Object, ProjectComponentIdentifier> projectIdentifierNotationParser;

    private MutationValidator mutationValidator = MutationValidator.IGNORE;
    private boolean hasDependencySubstitutionRule;

    public DefaultDependencySubstitutions() {
        this(new LinkedHashSet<Action<? super DependencySubstitution>>());
    }

    DefaultDependencySubstitutions(Set<Action<? super DependencySubstitution>> substitutionRules) {
        this.substitutionRules = substitutionRules;
        this.moduleIdentifierNotationParser = createModuleIdentifierNotationParser();
        this.projectIdentifierNotationParser = createProjectIdentifierNotationParser();
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
        return ComponentSelectorParsers.parser().parseNotation(notation);
    }

    @Override
    public ComponentSelector project(final String path) {
        return new DefaultProjectComponentSelector(path);
    }

    @Override
    public Substitution substitute(final ComponentSelector substituted) {
        return new Substitution() {
            @Override
            public void with(final ComponentSelector substitute) {
                all(new Action<DependencySubstitution>() {
                    @Override
                    public void execute(DependencySubstitution dependencySubstitution) {
                        if (substituted.equals(dependencySubstitution.getRequested())) {
                            dependencySubstitution.useTarget(substitute);
                        }
                    }
                });
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

    private static NotationParser<Object, ModuleIdentifier> createModuleIdentifierNotationParser() {
        return NotationParserBuilder
                .toType(ModuleIdentifier.class)
                .converter(new ModuleIdentifierNotationConverter())
                .converter(ModuleIdentifierMapNotationConverter.getInstance())
                .toComposite();
    }

    private static class ModuleIdentifierMapNotationConverter extends MapNotationConverter<ModuleIdentifier> {

        private final static ModuleIdentifierMapNotationConverter INSTANCE = new ModuleIdentifierMapNotationConverter();

        public static ModuleIdentifierMapNotationConverter getInstance() {
            return INSTANCE;
        }

        @Override
        public void describe(DiagnosticsVisitor visitor) {
            visitor.example("Maps, e.g. [group: 'org.gradle', name:'gradle-core'].");
        }

        protected ModuleIdentifier parseMap(@MapKey("group") String group, @MapKey("name") String name) {
            return DefaultModuleIdentifier.newId(group, name);
        }
    }

    private static NotationParser<Object, ProjectComponentIdentifier> createProjectIdentifierNotationParser() {
        return NotationParserBuilder
                .toType(ProjectComponentIdentifier.class)
                .fromCharSequence(new ProjectPathConverter())
                .fromType(Project.class, new ProjectConverter())
                .toComposite();
    }

    private static class ProjectPathConverter implements NotationConverter<String, ProjectComponentIdentifier> {
        @Override
        public void describe(DiagnosticsVisitor visitor) {
            visitor.example("Project paths, e.g. ':api'.");
        }

        @Override
        public void convert(String notation, NotationConvertResult<? super ProjectComponentIdentifier> result) throws TypeConversionException {
            result.converted(DefaultProjectComponentIdentifier.newId(notation));
        }
    }

    private static class ProjectConverter implements NotationConverter<Project, ProjectComponentIdentifier> {

        @Override
        public void describe(DiagnosticsVisitor visitor) {
            visitor.example("Project objects, e.g. project(':api').");
        }

        @Override
        public void convert(Project notation, NotationConvertResult<? super ProjectComponentIdentifier> result) throws TypeConversionException {
            result.converted(DefaultProjectComponentIdentifier.newId(notation.getPath()));
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
