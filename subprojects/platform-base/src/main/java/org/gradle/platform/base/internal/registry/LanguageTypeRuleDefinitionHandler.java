/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.platform.base.internal.registry;

import org.gradle.api.Action;
import org.gradle.api.NamedDomainObjectFactory;
import org.gradle.api.NamedDomainObjectSet;
import org.gradle.api.internal.file.BaseDirFileResolver;
import org.gradle.internal.nativeintegration.services.FileSystems;
import org.gradle.internal.reflect.DirectInstantiator;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.reflect.JavaReflectionUtil;
import org.gradle.language.base.FunctionalSourceSet;
import org.gradle.language.base.plugins.ComponentModelBasePlugin;
import org.gradle.model.internal.core.ModelMutator;
import org.gradle.model.internal.inspect.MethodRuleDefinition;
import org.gradle.model.internal.inspect.RuleSourceDependencies;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.model.internal.type.ModelType;
import org.gradle.platform.base.*;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.platform.base.internal.ComponentSpecInternal;
import org.gradle.language.base.sources.BaseLanguageSourceSet;

import java.io.File;

public class LanguageTypeRuleDefinitionHandler extends TypeRuleDefinitionHandler<LanguageType, LanguageSourceSet, BaseLanguageSourceSet> {

    private Instantiator instantiator;

    public LanguageTypeRuleDefinitionHandler(final Instantiator instantiator) {
        super("language", LanguageSourceSet.class, BaseLanguageSourceSet.class, LanguageTypeBuilder.class, JavaReflectionUtil.factory(new DirectInstantiator(), DefaultLanguageTypeBuilder.class));
        this.instantiator = instantiator;
    }

    @Override
    <R> void doRegister(MethodRuleDefinition<R> ruleDefinition, ModelRegistry modelRegistry, RuleSourceDependencies dependencies, ModelType<? extends LanguageSourceSet> type, TypeBuilderInternal<LanguageSourceSet> builder) {
        ModelType<? extends BaseLanguageSourceSet> implementation = determineImplementationType(type, builder);
        dependencies.add(ComponentModelBasePlugin.class);
        if (implementation != null) {
            ModelMutator<?> mutator = new RegisterTypeRule<LanguageSourceSet, BaseLanguageSourceSet>(type, implementation, ruleDefinition.getDescriptor(), new RegistrationAction(instantiator));
            modelRegistry.mutate(mutator);
        }
    }

    public static class DefaultLanguageTypeBuilder extends AbstractTypeBuilder<LanguageSourceSet> implements LanguageTypeBuilder<LanguageSourceSet> {
        private String languageName;

        public DefaultLanguageTypeBuilder() {
            super(LanguageType.class);
        }

        @Override
        public void setLanguageName(String languageName) {
            this.languageName = languageName;
        }
    }

    private static class RegistrationAction implements Action<RegistrationContext<LanguageSourceSet, BaseLanguageSourceSet>> {
        private final Instantiator instantiator;

        public RegistrationAction(Instantiator instantiator) {
            this.instantiator = instantiator;
        }

        @Override
        public void execute(RegistrationContext<LanguageSourceSet, BaseLanguageSourceSet> context) {
            ComponentSpecContainer componentSpecs = context.getExtensions().getByType(ComponentSpecContainer.class);

            doRegister(componentSpecs, context.getProjectIdentifier().getProjectDir(), context.getType(), context.getImplementation());
        }

        private <T extends LanguageSourceSet, U extends BaseLanguageSourceSet> void doRegister(ComponentSpecContainer componentSpecs, final File projectDir, final ModelType<T> type, final ModelType<U> implementation) {
            NamedDomainObjectSet<ComponentSpecInternal> componentSpecsInternals = componentSpecs.withType(ComponentSpecInternal.class);
            final BaseDirFileResolver fileResolver = new BaseDirFileResolver(FileSystems.getDefault(), projectDir);
            // TODO Rene: we should have an implicit rule dependency on componentSpecs instead of using .all here
            componentSpecsInternals.all(new Action<ComponentSpecInternal>() {
                @Override
                public void execute(ComponentSpecInternal componentSpecInternal) {
                    final FunctionalSourceSet functionalSourceSet = componentSpecInternal.getSources();
                    functionalSourceSet.registerFactory(type.getConcreteClass(), new NamedDomainObjectFactory<T>() {
                        public T create(String name) {
                            // safe because we implicitly know that U extends V, but can't express this in the type system
                            @SuppressWarnings("unchecked")
                            T created = (T) BaseLanguageSourceSet.create(implementation.getConcreteClass(), name, functionalSourceSet.getName(), fileResolver, instantiator);
                            return created;
                        }
                    });
                }
            });
        }
    }
}
