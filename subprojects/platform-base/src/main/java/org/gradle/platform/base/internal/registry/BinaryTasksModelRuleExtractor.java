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

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import org.gradle.api.*;
import org.gradle.api.specs.Spec;
import org.gradle.internal.Actions;
import org.gradle.internal.Cast;
import org.gradle.language.base.plugins.ComponentModelBasePlugin;
import org.gradle.model.InvalidModelRuleDeclarationException;
import org.gradle.model.ModelMap;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor;
import org.gradle.model.internal.inspect.MethodRuleDefinition;
import org.gradle.model.internal.type.ModelType;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.BinaryTasks;
import org.gradle.platform.base.InvalidModelException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class BinaryTasksModelRuleExtractor extends AbstractAnnotationDrivenComponentModelRuleExtractor<BinaryTasks> {

    public <R, S> ExtractedModelRule registration(MethodRuleDefinition<R, S> ruleDefinition) {
        return createRegistration(ruleDefinition);
    }

    private <R, S extends BinarySpec> ExtractedModelRule createRegistration(MethodRuleDefinition<R, ?> ruleDefinition) {
        try {
            RuleMethodDataCollector dataCollector = new RuleMethodDataCollector();
            verifyMethodSignature(dataCollector, ruleDefinition);

            final Class<S> binaryType = dataCollector.getParameterType(BinarySpec.class);

            final BinaryTaskRule<R, S> binaryTaskRule = new BinaryTaskRule<R, S>(binaryType, ruleDefinition);
            return new ExtractedModelAction(ModelActionRole.Defaults, ImmutableList.of(ComponentModelBasePlugin.class), DirectNodeModelAction.of(ModelReference.of("binaries"), new SimpleModelRuleDescriptor("binaries*.create()"), new Action<MutableModelNode>() {
                @Override
                public void execute(MutableModelNode modelNode) {
                    modelNode.applyToAllLinks(ModelActionRole.Finalize, binaryTaskRule);
                }
            }));
        } catch (InvalidModelException e) {
            throw invalidModelRule(ruleDefinition, e);
        }
    }

    private void verifyMethodSignature(RuleMethodDataCollector taskDataCollector, MethodRuleDefinition<?, ?> ruleDefinition) {
        assertIsVoidMethod(ruleDefinition);
        visitSubject(taskDataCollector, ruleDefinition, Task.class);
        visitDependency(taskDataCollector, ruleDefinition, ModelType.of(BinarySpec.class));
    }

    //TODO extract common general method reusable by all AnnotationRuleDefinitionHandler
    protected InvalidModelRuleDeclarationException invalidModelRule(MethodRuleDefinition<?, ?> ruleDefinition, InvalidModelException e) {
        StringBuilder sb = new StringBuilder();
        ruleDefinition.getDescriptor().describeTo(sb);
        sb.append(" is not a valid BinaryTask model rule method.");
        return new InvalidModelRuleDeclarationException(sb.toString(), e);
    }

    private class BinaryTaskRule<R, T extends BinarySpec> extends ModelMapBasedRule<R, Task, T, T> {

        public BinaryTaskRule(Class<T> binaryType, MethodRuleDefinition<R, ?> ruleDefinition) {
            super(ModelReference.of(binaryType), binaryType, ruleDefinition);
        }

        public void execute(final MutableModelNode modelNode, final T binary, List<ModelView<?>> inputs) {

            ModelMap<Task> cast = new DomainObjectSetBackedModelMap<Task>(
                binary,
                Task.class,
                binary.getTasks(),
                new NamedEntityInstantiator<Task>() {
                    @Override
                    public <S extends Task> S create(String name, Class<S> type) {
                        modelNode.getPrivateData(ModelType.of(BinarySpec.class)).getTasks().create(name, type, Actions.doNothing());
                        return null; // we know it's not needed
                    }
                },
                new Namer<Object>() {
                    @Override
                    public String determineName(Object object) {
                        return Cast.cast(Task.class, object).getName();
                    }
                }
            );

            List<ModelView<?>> inputsWithBinary = new ArrayList<ModelView<?>>(inputs.size() + 1);
            inputsWithBinary.addAll(inputs);
            inputsWithBinary.add(InstanceModelView.of(getSubject().getPath(), getSubject().getType(), binary));

            invoke(inputsWithBinary, cast, binary, binary);
        }
    }

    private static class DomainObjectSetBackedModelMap<T> extends DomainObjectCollectionBackedModelMap<T, DomainObjectSet<T>> {

        private final BinarySpec binary;

        public DomainObjectSetBackedModelMap(BinarySpec binary, Class<T> elementClass, DomainObjectSet<T> backingSet, NamedEntityInstantiator<T> instantiator, Namer<Object> namer) {
            super(elementClass, backingSet, instantiator, namer);
            this.binary = binary;
        }

        @Override
        protected <S> ModelMap<S> toNonSubtypeMap(Class<S> type) {
            DomainObjectSet<S> cast = toNonSubtype(type);
            return new DomainObjectSetBackedModelMap<S>(binary, type, cast, new NamedEntityInstantiator<S>() {
                @Override
                public <D extends S> D create(String name, Class<D> type) {
                    throw new IllegalArgumentException(String.format("Cannot create an item of type %s as this is not a subtype of %s.", type, elementClass.toString()));
                }
            }, namer);
        }

        private <S> DomainObjectSet<S> toNonSubtype(final Class<S> type) {
            DomainObjectSet<T> matching = backingCollection.matching(new Spec<T>() {
                @Override
                public boolean isSatisfiedBy(T element) {
                    return type.isInstance(element);
                }
            });

            return Cast.uncheckedCast(matching);
        }

        protected <S extends T> ModelMap<S> toSubtypeMap(Class<S> itemSubtype) {
            NamedEntityInstantiator<S> instantiator = Cast.uncheckedCast(this.instantiator);
            return new DomainObjectSetBackedModelMap<S>(binary, itemSubtype, backingCollection.withType(itemSubtype), instantiator, namer);
        }

        @Nullable
        @Override
        public T get(String name) {
            return Iterables.find(backingCollection, new HasNamePredicate<T>(name, namer), null);
        }

        @Override
        public Set<String> keySet() {
            return Sets.newHashSet(Iterables.transform(backingCollection, new ToName<T>(namer)));
        }

        @Override
        protected void onCreate(T item) {
            binary.builtBy(item);
        }

        @Override
        public <S> void withType(Class<S> type, Action<? super S> configAction) {
            toNonSubtype(type).all(configAction);
        }
    }

    private static class HasNamePredicate<T> implements Predicate<T> {
        private final String name;
        private final Namer<? super T> namer;

        public HasNamePredicate(String name, Namer<? super T> namer) {
            this.name = name;
            this.namer = namer;
        }

        @Override
        public boolean apply(@Nullable T input) {
            return namer.determineName(input).equals(name);
        }
    }

    private static class ToName<T> implements Function<T, String> {
        private final Namer<? super T> namer;

        public ToName(Namer<? super T> namer) {
            this.namer = namer;
        }

        @Override
        public String apply(@Nullable T input) {
            return namer.determineName(input);
        }
    }
}
