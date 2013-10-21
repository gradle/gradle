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

package org.gradle.model.internal.rules;

import com.google.common.collect.ImmutableList;
import org.gradle.api.Nullable;
import org.gradle.api.Transformer;
import org.gradle.api.specs.Spec;
import org.gradle.internal.UncheckedException;
import org.gradle.model.ModelFinalizer;
import org.gradle.model.ModelPath;
import org.gradle.model.ModelRule;
import org.gradle.model.Path;
import org.gradle.model.internal.Inputs;
import org.gradle.model.internal.ModelCreationListener;
import org.gradle.model.internal.ModelMutator;
import org.gradle.model.internal.ModelRegistry;
import org.gradle.util.CollectionUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static java.lang.reflect.Modifier.isPrivate;
import static java.lang.reflect.Modifier.isStatic;
import static org.gradle.util.CollectionUtils.*;

public abstract class ReflectiveRule {

    public static void rule(final ModelRegistry modelRegistry, final ModelRule modelRule) {
        final Method bindingMethod = findBindingMethod(modelRule);
        final List<BindableParameter<?>> initialBindings = bindings(bindingMethod);

        boolean unsatisfied = CollectionUtils.any(initialBindings, new Spec<BindableParameter<?>>() {
            public boolean isSatisfiedBy(BindableParameter<?> element) {
                return element.getPath() == null;
            }
        });

        if (unsatisfied) {
            modelRegistry.registerListener(new ModelCreationListener() {

                private List<BindableParameter<?>> bindings = initialBindings;

                public boolean onCreate(ModelPath path, Class<?> type) {
                    ImmutableList.Builder<BindableParameter<?>> bindingsBuilder = ImmutableList.builder();

                    boolean unsatisfied = false;

                    for (BindableParameter<?> binding : bindings) {
                        if (binding.getPath() == null) {
                            if (binding.getType().isAssignableFrom(type)) {
                                bindingsBuilder.add(copyBindingWithPath(path, binding));
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
                        registerMutator(modelRegistry, modelRule, bindingMethod, bindings);
                        return true;
                    }
                }
            });
        } else {
            registerMutator(modelRegistry, modelRule, bindingMethod, initialBindings);
        }
    }

    private static void registerMutator(ModelRegistry modelRegistry, ModelRule modelRule, final Method bindingMethod, final List<BindableParameter<?>> bindings) {
        BindableParameter<?> first = bindings.get(0);
        List<BindableParameter<?>> tail = bindings.subList(1, bindings.size());
        ModelMutator<?> modelMutator = toMutator(modelRule, bindingMethod, first, tail);

        String path = first.getPath().toString();
        List<String> bindingPaths = CollectionUtils.collect(tail, new Transformer<String, BindableParameter<?>>() {
            public String transform(BindableParameter<?> bindableParameter) {
                return bindableParameter.getPath().toString();
            }
        });

        if (modelRule instanceof ModelFinalizer) {
            modelRegistry.finalize(path, bindingPaths, modelMutator);
        } else {
            modelRegistry.mutate(path, bindingPaths, modelMutator);
        }
    }

    private static <T> ModelMutator<T> toMutator(final ModelRule modelRule, final Method bindingMethod, final BindableParameter<T> first, final List<BindableParameter<?>> tail) {
        return new ModelMutator<T>() {
            public Class<T> getType() {
                return first.getType();
            }

            public void mutate(T object, Inputs inputs) {
                Object[] args = new Object[1 + tail.size()];
                args[0] = object;
                for (int i = 0; i < inputs.size(); ++i) {
                    args[i + 1] = inputs.get(i, tail.get(i).getType());
                }

                bindingMethod.setAccessible(true);

                try {
                    bindingMethod.invoke(modelRule, args);
                } catch (Exception e) {
                    Throwable t = e;
                    if (t instanceof InvocationTargetException) {
                        t = e.getCause();
                    }

                    UncheckedException.throwAsUncheckedException(t);
                }
            }
        };
    }

    private static <T> BindableParameter<T> copyBindingWithPath(ModelPath path, BindableParameter<T> binding) {
        return new BindableParameter<T>(path, binding.getType());
    }

    public static Method findBindingMethod(Object object) {
        Class<?> objectClass = object.getClass();
        List<Method> declaredMethods = filter(Arrays.asList(objectClass.getDeclaredMethods()), new Spec<Method>() {
            public boolean isSatisfiedBy(Method element) {
                int modifiers = element.getModifiers();
                return !isPrivate(modifiers) && !isStatic(modifiers) && !element.isSynthetic();
            }
        });
        if (declaredMethods.size() != 1) {
            throw new IllegalArgumentException(objectClass + " rule must have exactly 1 public method, has: " + join(", ", toStringList(declaredMethods)));
        }

        return declaredMethods.get(0);
    }

    private static List<BindableParameter<?>> bindings(Method method) {
        return bindings(method.getParameterTypes(), method.getParameterAnnotations());
    }

    static List<BindableParameter<?>> bindings(Class[] types, Annotation[][] annotations) {
        ImmutableList.Builder<BindableParameter<?>> inputBindingBuilder = ImmutableList.builder();

        for (int i = 0; i < types.length; i++) {
            Class<?> paramType = types[i];
            Annotation[] paramAnnotations = annotations[i];

            inputBindingBuilder.add(binding(paramType, paramAnnotations));
        }

        return inputBindingBuilder.build();
    }

    private static <T> BindableParameter binding(Class<T> type, Annotation[] annotations) {
        Path pathAnnotation = (Path) findFirst(annotations, new Spec<Annotation>() {
            public boolean isSatisfiedBy(Annotation element) {
                return element.annotationType().equals(Path.class);
            }
        });
        String path = pathAnnotation == null ? null : pathAnnotation.value();
        return new BindableParameter<T>(path == null ? null : ModelPath.path(path), type);
    }


    public static class BindableParameter<T> {

        private final ModelPath path;
        private final Class<T> type;

        public BindableParameter(@Nullable ModelPath path, Class<T> type) {
            this.path = path;
            this.type = type;
        }

        public ModelPath getPath() {
            return path;
        }

        public Class<T> getType() {
            return type;
        }
    }
}
