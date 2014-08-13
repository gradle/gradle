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

package org.gradle.model.internal.inspect;

import com.google.common.collect.ImmutableList;
import org.gradle.api.specs.Spec;
import org.gradle.model.Path;
import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.core.ModelReference;
import org.gradle.model.internal.core.ModelType;
import org.gradle.model.internal.core.rule.describe.MethodModelRuleDescriptor;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.List;

import static org.gradle.util.CollectionUtils.findFirst;

public class DefaultMethodRuleDefinition implements MethodRuleDefinition {
    private final Method method;

    public DefaultMethodRuleDefinition(Method method) {
        this.method = method;
    }

    public String getMethodName() {
        return method.getName();
    }

    public ModelType<?> getReturnType() {
        return ModelType.of(method.getGenericReturnType());
    }

    public List<ModelReference<?>> getReferences() {
        Type[] types = method.getGenericParameterTypes();
        ImmutableList.Builder<ModelReference<?>> inputBindingBuilder = ImmutableList.builder();
        for (int i = 0; i < types.length; i++) {
            Type paramType = types[i];
            Annotation[] paramAnnotations = method.getParameterAnnotations()[i];
            inputBindingBuilder.add(reference(paramType, paramAnnotations, i));
        }
        return inputBindingBuilder.build();
    }

    public ModelRuleDescriptor getDescriptor() {
        return new MethodModelRuleDescriptor(method);
    }

    public ModelRuleInvoker getRuleInvoker() {
        return new DefaultModelRuleInvoker(method);
    }

    public <T extends Annotation> T getAnnotation(Class<T> annotationType) {
        return method.getAnnotation(annotationType);
    }

    private <T> ModelReference<T> reference(Type type, Annotation[] annotations, int i) {
        Path pathAnnotation = (Path) findFirst(annotations, new Spec<Annotation>() {
            public boolean isSatisfiedBy(Annotation element) {
                return element.annotationType().equals(Path.class);
            }
        });
        String path = pathAnnotation == null ? null : pathAnnotation.value();
        @SuppressWarnings("unchecked") ModelType<T> cast = (ModelType<T>) ModelType.of(type);
        return ModelReference.of(path == null ? null : ModelPath.path(path), cast, String.format("parameter %s", i + 1));
    }

}
