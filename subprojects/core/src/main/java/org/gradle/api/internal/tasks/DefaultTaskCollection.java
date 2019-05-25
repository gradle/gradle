/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.tasks;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;
import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.NamedDomainObjectCollectionSchema;
import org.gradle.api.Task;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.internal.DefaultNamedDomainObjectSet;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.MutationGuard;
import org.gradle.api.internal.collections.CollectionFilter;
import org.gradle.api.internal.plugins.DslObject;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.provider.ProviderInternal;
import org.gradle.api.reflect.TypeOf;
import org.gradle.api.specs.Spec;
import org.gradle.api.specs.Specs;
import org.gradle.api.tasks.TaskCollection;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.internal.Cast;
import org.gradle.internal.reflect.Instantiator;

import java.util.Map;

import static org.gradle.api.reflect.TypeOf.typeOf;

public class DefaultTaskCollection<T extends Task> extends DefaultNamedDomainObjectSet<T> implements TaskCollection<T> {
    private static final Task.Namer NAMER = new Task.Namer();

    protected final ProjectInternal project;

    private final MutationGuard parentMutationGuard;

    public DefaultTaskCollection(Class<T> type, Instantiator instantiator, ProjectInternal project, MutationGuard parentMutationGuard, CollectionCallbackActionDecorator decorator) {
        super(type, instantiator, NAMER, decorator);
        this.project = project;
        this.parentMutationGuard = parentMutationGuard;
    }

    public DefaultTaskCollection(DefaultTaskCollection<? super T> collection, CollectionFilter<T> filter, Instantiator instantiator, ProjectInternal project, MutationGuard parentMutationGuard) {
        super(collection, filter, instantiator, NAMER);
        this.project = project;
        this.parentMutationGuard = parentMutationGuard;
    }

    @Override
    protected <S extends T> DefaultTaskCollection<S> filtered(CollectionFilter<S> filter) {
        return getInstantiator().newInstance(DefaultTaskCollection.class, this, filter, getInstantiator(), project, parentMutationGuard);
    }

    @Override
    public <S extends T> TaskCollection<S> withType(Class<S> type) {
        return filtered(createFilter(type));
    }

    @Override
    public TaskCollection<T> matching(Spec<? super T> spec) {
        return filtered(createFilter(spec));
    }

    @Override
    public TaskCollection<T> matching(Closure spec) {
        return matching(Specs.<T>convertClosureToSpec(spec));
    }

    @Override
    public Action<? super T> whenTaskAdded(Action<? super T> action) {
        return whenObjectAdded(action);
    }

    @Override
    public void whenTaskAdded(Closure closure) {
        whenObjectAdded(closure);
    }

    @Override
    public String getTypeDisplayName() {
        return "task";
    }

    @Override
    protected UnknownTaskException createNotFoundException(String name) {
        return new UnknownTaskException(String.format("Task with name '%s' not found in %s.", name, project));
    }

    @Override
    protected InvalidUserDataException createWrongTypeException(String name, Class expected, Class actual) {
        return new InvalidUserDataException(String.format("The task '%s' (%s) is not a subclass of the given type (%s).", name, actual.getCanonicalName(), expected.getCanonicalName()));
    }

    @Override
    public TaskProvider<T> named(String name) throws UnknownTaskException {
        return (TaskProvider<T>) super.named(name);
    }

    @Override
    public TaskProvider<T> named(String name, Action<? super T> configurationAction) throws UnknownTaskException {
        return (TaskProvider<T>) super.named(name, configurationAction);
    }

    @Override
    public <S extends T> TaskProvider<S> named(String name, Class<S> type) throws UnknownTaskException {
        return (TaskProvider<S>) super.named(name, type);
    }

    @Override
    public <S extends T> TaskProvider<S> named(String name, Class<S> type, Action<? super S> configurationAction) throws UnknownTaskException {
        return (TaskProvider<S>) super.named(name, type, configurationAction);
    }

    @Override
    protected TaskProvider<? extends T> createExistingProvider(String name, T object) {
        // TODO: This isn't quite right. We're leaking the _implementation_ type here.  But for tasks, this is usually right.
        return Cast.uncheckedCast(getInstantiator().newInstance(ExistingTaskProvider.class, this, object.getName(), new DslObject(object).getDeclaredType()));
    }

    @Override
    protected <I extends T> Action<? super I> withMutationDisabled(Action<? super I> action) {
        return parentMutationGuard.withMutationDisabled(super.withMutationDisabled(action));
    }

    @Override
    public NamedDomainObjectCollectionSchema getCollectionSchema() {
        return new NamedDomainObjectCollectionSchema() {
            @Override
            public Iterable<? extends NamedDomainObjectSchema> getElements() {
                return Iterables.concat(
                    Iterables.transform(index.asMap().entrySet(), new Function<Map.Entry<String, T>, NamedDomainObjectSchema>() {
                        @Override
                        public NamedDomainObjectSchema apply(final Map.Entry<String, T> e) {
                            return new NamedDomainObjectSchema() {
                                @Override
                                public String getName() {
                                    return e.getKey();
                                }

                                @Override
                                public TypeOf<?> getPublicType() {
                                    // TODO: This returns the wrong public type for domain objects
                                    // created with the eager APIs or added directly to the container.
                                    // This can leak internal types.
                                    // We do not currently keep track of the type used when creating
                                    // a domain object (via create) or the type of the container when
                                    // a domain object is added directly (via add).
                                    return new DslObject(e.getValue()).getPublicType();
                                }
                            };
                        }
                    }),
                    Iterables.transform(index.getPendingAsMap().entrySet(), new Function<Map.Entry<String, ProviderInternal<? extends T>>, NamedDomainObjectSchema>() {
                        @Override
                        public NamedDomainObjectSchema apply(final Map.Entry<String, ProviderInternal<? extends T>> e) {
                            return new NamedDomainObjectSchema() {
                                @Override
                                public String getName() {
                                    return e.getKey();
                                }

                                @Override
                                public TypeOf<?> getPublicType() {
                                    return typeOf(e.getValue().getType());
                                }
                            };
                        }
                    })
                );
            }
        };
    }

    // Cannot be private due to reflective instantiation
    public class ExistingTaskProvider<I extends T> extends ExistingNamedDomainObjectProvider<I> implements TaskProvider<I> {
        public ExistingTaskProvider(String name, Class type) {
            super(name, type);
        }

        @Override
        public boolean maybeVisitBuildDependencies(TaskDependencyResolveContext context) {
            context.add(get());
            return true;
        }
    }
}
