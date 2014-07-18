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
import org.gradle.api.specs.Spec;
import org.gradle.internal.Factory;
import org.gradle.internal.UncheckedException;
import org.gradle.model.Path;
import org.gradle.model.internal.core.Inputs;
import org.gradle.model.internal.core.ModelBinding;
import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.core.ModelType;
import org.gradle.model.internal.core.rule.describe.MethodModelRuleDescriptor;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;

import static org.gradle.util.CollectionUtils.findFirst;

public abstract class ReflectiveRule {

    public static void rule(final ModelRegistry modelRegistry, final Method method, final boolean isFinalizer, final Factory<?> instance) {
        List<ModelBinding<?>> bindings = bindings(method);

        ModelBinding<?> subject = bindings.get(0);
        List<ModelBinding<?>> inputs = bindings.subList(1, bindings.size());
        MethodModelMutator<?> mutator = toMutator(method, instance, subject, inputs);

        if (isFinalizer) {
            modelRegistry.finalize(mutator);
        } else {
            modelRegistry.mutate(mutator);
        }
    }

    private static <T> MethodModelMutator<T> toMutator(Method method, Factory<?> instance, ModelBinding<T> first, List<ModelBinding<?>> tail) {
        return new MethodModelMutator<T>(method, first, tail, instance);
    }

    private static List<ModelBinding<?>> bindings(Method method) {
        Type[] types = method.getGenericParameterTypes();
        ImmutableList.Builder<ModelBinding<?>> inputBindingBuilder = ImmutableList.builder();
        for (int i = 0; i < types.length; i++) {
            Type paramType = types[i];
            Annotation[] paramAnnotations = method.getParameterAnnotations()[i];
            inputBindingBuilder.add(binding(paramType, paramAnnotations));
        }
        return inputBindingBuilder.build();
    }

    private static <T> ModelBinding<T> binding(Type type, Annotation[] annotations) {
        Path pathAnnotation = (Path) findFirst(annotations, new Spec<Annotation>() {
            public boolean isSatisfiedBy(Annotation element) {
                return element.annotationType().equals(Path.class);
            }
        });
        String path = pathAnnotation == null ? null : pathAnnotation.value();
        @SuppressWarnings("unchecked") TypeToken<T> cast = (TypeToken<T>) TypeToken.of(type);
        return ModelBinding.of(path == null ? null : ModelPath.path(path), ModelType.of(cast));
    }

    private static class MethodModelMutator<T> implements org.gradle.model.internal.core.ModelMutator<T> {
        private final MethodModelRuleDescriptor descriptor;
        private final Method bindingMethod;
        private final ModelBinding<T> subject;
        private final List<ModelBinding<?>> inputs;
        private final Factory<?> instance;

        public MethodModelMutator(Method method, ModelBinding<T> subject, List<ModelBinding<?>> inputs, Factory<?> instance) {
            this.bindingMethod = method;
            this.subject = subject;
            this.inputs = inputs;
            this.instance = instance;
            this.descriptor = new MethodModelRuleDescriptor(method);
        }

        public ModelRuleDescriptor getDescriptor() {
            return descriptor;
        }

        public ModelBinding<T> getBinding() {
            return subject;
        }

        public List<ModelBinding<?>> getInputBindings() {
            return inputs;
        }

        public void mutate(T object, Inputs inputs) {
            Object[] args = new Object[1 + this.inputs.size()];
            args[0] = object;
            for (int i = 0; i < inputs.size(); ++i) {
                args[i + 1] = inputs.get(i, this.inputs.get(i).getType()).getInstance();
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
    }
}
