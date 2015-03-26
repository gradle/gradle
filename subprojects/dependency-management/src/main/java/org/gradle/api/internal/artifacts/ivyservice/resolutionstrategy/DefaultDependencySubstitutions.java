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

package org.gradle.api.internal.artifacts.ivyservice.resolutionstrategy;

import com.google.common.base.Objects;
import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.*;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.internal.ClosureBackedAction;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.DependencySubstitutionInternal;
import org.gradle.api.internal.artifacts.configurations.MutationValidator;
import org.gradle.api.internal.artifacts.ivyservice.DefaultDependencyResolveDetails;
import org.gradle.api.internal.notations.ModuleIdentiferNotationConverter;
import org.gradle.internal.Actions;
import org.gradle.internal.component.local.model.DefaultProjectComponentIdentifier;
import org.gradle.internal.exceptions.DiagnosticsVisitor;
import org.gradle.internal.typeconversion.*;

import java.util.LinkedHashSet;
import java.util.Set;

public class DefaultDependencySubstitutions implements DependencySubstitutionsInternal {
    private final Set<Action<? super DependencySubstitution<? super ComponentSelector>>> substitutionRules;
    private final NotationParser<Object, ModuleIdentifier> moduleIdentifierNotationParser;
    private final NotationParser<Object, ProjectComponentIdentifier> projectIdentifierNotationParser;

    private MutationValidator mutationValidator = MutationValidator.IGNORE;

    public DefaultDependencySubstitutions() {
        this(new LinkedHashSet<Action<? super DependencySubstitution<? super ComponentSelector>>>());
    }

    DefaultDependencySubstitutions(Set<Action<? super DependencySubstitution<? super ComponentSelector>>> substitutionRules) {
        this.substitutionRules = substitutionRules;
        this.moduleIdentifierNotationParser = createModuleIdentifierNotationParser();
        this.projectIdentifierNotationParser = createProjectIdentifierNotationParser();
    }

    @Override
    public Action<DependencySubstitution<ComponentSelector>> getDependencySubstitutionRule() {
        return Actions.composite(substitutionRules);
    }

    private void addRule(Action<? super DependencySubstitution<? super ComponentSelector>> rule) {
        mutationValidator.validateMutation(MutationValidator.MutationType.STRATEGY);
        substitutionRules.add(rule);
    }

    @Override
    public DependencySubstitutions all(Action<? super DependencySubstitution<? super ComponentSelector>> rule) {
        addRule(rule);
        return this;
    }

    @Override
    public DependencySubstitutions allWithDependencyResolveDetails(Action<? super DependencyResolveDetails> rule) {
        addRule(new DependencyResolveDetailsWrapperAction(rule));
        return this;
    }

    @Override
    public DependencySubstitutions all(Closure<?> action) {
        return all(ClosureBackedAction.of(action));
    }

    @Override
    public DependencySubstitutions eachModule(Action<? super ModuleDependencySubstitution> rule) {
        return all(new TypeFilteringDependencySubstitutionAction<ModuleDependencySubstitution>(ModuleDependencySubstitution.class, rule));
    }

    @Override
    public DependencySubstitutions eachModule(Closure<?> rule) {
        return eachModule(ClosureBackedAction.of(rule));
    }

    @Override
    public DependencySubstitutions withModule(Object id, Action<? super ModuleDependencySubstitution> rule) {
        ModuleIdentifier moduleId = moduleIdentifierNotationParser.parseNotation(id);
        return all(new ModuleIdFilteringDependencySubstitutionAction(moduleId, rule));
    }

    @Override
    public DependencySubstitutions withModule(Object id, Closure<?> action) {
        return withModule(id, ClosureBackedAction.of(action));
    }

    @Override
    public DependencySubstitutions eachProject(Action<? super ProjectDependencySubstitution> rule) {
        return all(new TypeFilteringDependencySubstitutionAction<ProjectDependencySubstitution>(ProjectDependencySubstitution.class, rule));
    }

    @Override
    public DependencySubstitutions eachProject(Closure<?> rule) {
        return eachProject(ClosureBackedAction.of(rule));
    }

    @Override
    public DependencySubstitutions withProject(Object id, Action<? super ProjectDependencySubstitution> rule) {
        ProjectComponentIdentifier componentId = projectIdentifierNotationParser.parseNotation(id);
        return all(new ProjectIdFilteringDependencySubstitutionAction(componentId, rule));
    }

    @Override
    public DependencySubstitutions withProject(Object id, Closure<?> rule) {
        return withProject(id, ClosureBackedAction.of(rule));
    }

    @Override
    public void beforeChange(MutationValidator validator) {
        mutationValidator = validator;
    }

    @Override
    public DependencySubstitutionsInternal copy() {
        return new DefaultDependencySubstitutions(new LinkedHashSet<Action<? super DependencySubstitution<? super ComponentSelector>>>(substitutionRules));
    }

    private static NotationParser<Object, ModuleIdentifier> createModuleIdentifierNotationParser() {
        return NotationParserBuilder
                .toType(ModuleIdentifier.class)
                .converter(new ModuleIdentiferNotationConverter())
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

    private static class ModuleIdFilteringDependencySubstitutionAction implements Action<DependencySubstitution<ComponentSelector>> {
        private final Action<? super ModuleDependencySubstitution> delegate;
        private final ModuleIdentifier id;

        public ModuleIdFilteringDependencySubstitutionAction(ModuleIdentifier id, Action<? super ModuleDependencySubstitution> delegate) {
            this.id = id;
            this.delegate = delegate;
        }

        @Override
        public void execute(DependencySubstitution substitution) {
            ComponentSelector requested = substitution.getRequested();
            if (requested instanceof ModuleComponentSelector) {
                ModuleComponentSelector requestedModule = (ModuleComponentSelector) requested;
                if (Objects.equal(requestedModule.getGroup(), id.getGroup())
                        && Objects.equal(requestedModule.getModule(), id.getName())) {
                    delegate.execute((ModuleDependencySubstitution) substitution);
                }
            }
        }
    }

    private static class ProjectIdFilteringDependencySubstitutionAction implements Action<DependencySubstitution<ComponentSelector>> {
        private final Action<? super ProjectDependencySubstitution> delegate;
        private final ProjectComponentIdentifier id;

        public ProjectIdFilteringDependencySubstitutionAction(ProjectComponentIdentifier id, Action<? super ProjectDependencySubstitution> delegate) {
            this.id = id;
            this.delegate = delegate;
        }

        @Override
        public void execute(DependencySubstitution substitution) {
            ComponentSelector requested = substitution.getRequested();
            if (requested.matchesStrictly(id)) {
                delegate.execute((ProjectDependencySubstitution) substitution);
            }
        }
    }

    private static class TypeFilteringDependencySubstitutionAction<T extends DependencySubstitution<?>> implements Action<DependencySubstitution<ComponentSelector>> {
        private final Class<T> type;
        private final Action<? super T> delegate;

        public TypeFilteringDependencySubstitutionAction(Class<T> type, Action<? super T> delegate) {
            this.type = type;
            this.delegate = delegate;
        }

        @Override
        @SuppressWarnings("unchecked")
        public void execute(DependencySubstitution<ComponentSelector> substitution) {
            if (type.isAssignableFrom(substitution.getClass())) {
                delegate.execute((T) substitution);
            }
        }
    }

    private static class DependencyResolveDetailsWrapperAction implements Action<DependencySubstitution<? extends ComponentSelector>> {
        private final Action<? super DependencyResolveDetails> delegate;

        public DependencyResolveDetailsWrapperAction(Action<? super DependencyResolveDetails> delegate) {
            this.delegate = delegate;
        }

        @Override
        public void execute(DependencySubstitution<? extends ComponentSelector> substitution) {
            DefaultDependencyResolveDetails details = new DefaultDependencyResolveDetails((DependencySubstitutionInternal<?>) substitution);
            delegate.execute(details);
        }
    }
}
