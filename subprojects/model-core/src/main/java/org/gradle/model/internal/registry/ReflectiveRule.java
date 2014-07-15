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

package org.gradle.model.internal.registry;

import com.google.common.collect.ImmutableList;
import com.google.common.reflect.TypeToken;
import org.gradle.api.Action;
import org.gradle.api.Nullable;
import org.gradle.api.Transformer;
import org.gradle.api.specs.Spec;
import org.gradle.internal.Factory;
import org.gradle.internal.UncheckedException;
import org.gradle.model.Path;
import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.core.ModelReference;
import org.gradle.model.internal.core.ModelType;
import org.gradle.model.internal.core.rule.Inputs;
import org.gradle.model.internal.core.rule.ModelCreationListener;
import org.gradle.model.internal.core.rule.ModelMutator;
import org.gradle.model.internal.core.rule.describe.MethodModelRuleSourceDescriptor;
import org.gradle.model.internal.core.rule.describe.ModelRuleSourceDescriptor;
import org.gradle.util.CollectionUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;

import static org.gradle.util.CollectionUtils.findFirst;

public abstract class ReflectiveRule {

    public static void rule(final ModelRegistry modelRegistry, final Method method, final boolean isFinalizer, final Factory<?> instance) {
        bind(modelRegistry, method, new Action<List<BindableParameter<?>>>() {
            public void execute(List<BindableParameter<?>> bindableParameters) {
                registerMutator(modelRegistry, method, bindableParameters, isFinalizer, instance);
            }
        });
    }

    public static void bind(final ModelRegistry modelRegistry, final Method bindingMethod, final Action<? super List<BindableParameter<?>>> onBound) {
        final List<BindableParameter<?>> initialBindings = bindings(bindingMethod);

        boolean unsatisfied = CollectionUtils.any(initialBindings, new Spec<BindableParameter<?>>() {
            public boolean isSatisfiedBy(BindableParameter<?> element) {
                return element.getPath() == null;
            }
        });

        if (unsatisfied) {
            modelRegistry.registerListener(new ModelCreationListener() {

                private List<BindableParameter<?>> bindings = initialBindings;

                public boolean onCreate(ModelReference<?> reference) {
                    ImmutableList.Builder<BindableParameter<?>> bindingsBuilder = ImmutableList.builder();

                    boolean unsatisfied = false;

                    for (BindableParameter<?> binding : bindings) {
                        if (binding.getPath() == null) {
                            if (binding.getType().isAssignableFrom(reference.getType())) {
                                bindingsBuilder.add(copyBindingWithPath(reference.getPath(), binding));
                                continue;
                            } else {
                                unsatisfied = true;
                            }
                        }

                        bindingsBuilder.add(binding);
                    }

                    bindings = bindingsBuilder.build();

                    if (unsatisfied) {
                        return false;
                    } else {
                        onBound.execute(bindings);
                        return true;
                    }
                }
            });
        } else {
            onBound.execute(initialBindings);
        }
    }

    private static void registerMutator(ModelRegistry modelRegistry, final Method bindingMethod, final List<BindableParameter<?>> bindings, boolean isFinalizer, Factory<?> instance) {
        BindableParameter<?> first = bindings.get(0);
        List<BindableParameter<?>> tail = bindings.subList(1, bindings.size());
        ModelMutator<?> modelMutator = toMutator(bindingMethod, first, tail, instance);

        String path = first.getPath().toString();
        List<String> bindingPaths = CollectionUtils.collect(tail, new Transformer<String, BindableParameter<?>>() {
            public String transform(BindableParameter<?> bindableParameter) {
                return bindableParameter.getPath().toString();
            }
        });

        if (isFinalizer) {
            modelRegistry.finalize(path, bindingPaths, modelMutator);
        } else {
            modelRegistry.mutate(path, bindingPaths, modelMutator);
        }
    }

    private static <T> ModelMutator<T> toMutator(final Method bindingMethod, final BindableParameter<T> first, final List<BindableParameter<?>> tail, final Factory<?> instance) {
        return new ModelMutator<T>() {

            private final MethodModelRuleSourceDescriptor methodModelRuleSourceDescriptor = new MethodModelRuleSourceDescriptor(bindingMethod);

            public ModelRuleSourceDescriptor getSourceDescriptor() {
                return methodModelRuleSourceDescriptor;
            }

            public ModelReference<T> getReference() {
                return new ModelReference<T>(first.path, first.type);
            }

            public void mutate(T object, Inputs inputs) {
                Object[] args = new Object[1 + tail.size()];
                args[0] = object;
                for (int i = 0; i < inputs.size(); ++i) {
                    args[i + 1] = inputs.get(i, tail.get(i).getType()).getInstance();
                }

                bindingMethod.setAccessible(true);

                try {
                    bindingMethod.invoke(instance.create(), args);
                } catch (Exception e) {
                    Throwable t = e;
                    if (t instanceof InvocationTargetException) {
                        t = e.getCause();
                    }

                    throw UncheckedException.throwAsUncheckedException(t);
                }
            }
        };
    }

    private static <T> BindableParameter<T> copyBindingWithPath(ModelPath path, BindableParameter<T> binding) {
        return new BindableParameter<T>(path, binding.getType());
    }

    private static List<BindableParameter<?>> bindings(Method method) {
        return bindings(method.getGenericParameterTypes(), method.getParameterAnnotations());
    }

    static List<BindableParameter<?>> bindings(Type[] types, Annotation[][] annotations) {
        ImmutableList.Builder<BindableParameter<?>> inputBindingBuilder = ImmutableList.builder();

        for (int i = 0; i < types.length; i++) {
            Type paramType = types[i];
            Annotation[] paramAnnotations = annotations[i];

            inputBindingBuilder.add(binding(paramType, paramAnnotations));
        }

        return inputBindingBuilder.build();
    }

    private static <T> BindableParameter binding(Type type, Annotation[] annotations) {
        Path pathAnnotation = (Path) findFirst(annotations, new Spec<Annotation>() {
            public boolean isSatisfiedBy(Annotation element) {
                return element.annotationType().equals(Path.class);
            }
        });
        String path = pathAnnotation == null ? null : pathAnnotation.value();
        @SuppressWarnings("unchecked") TypeToken<T> cast = (TypeToken<T>) TypeToken.of(type);
        return new BindableParameter<T>(path == null ? null : ModelPath.path(path), new ModelType<T>(cast));
    }


    public static class BindableParameter<T> {

        private final ModelPath path;
        private final ModelType<T> type;

        public BindableParameter(@Nullable ModelPath path, ModelType<T> type) {
            this.path = path;
            this.type = type;
        }

        public ModelPath getPath() {
            return path;
        }

        public ModelType<T> getType() {
            return type;
        }
    }
}
