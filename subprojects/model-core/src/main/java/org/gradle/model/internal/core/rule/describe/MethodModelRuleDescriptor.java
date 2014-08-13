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

package org.gradle.model.internal.core.rule.describe;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.common.reflect.TypeToken;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.specs.Spec;
import org.gradle.util.CollectionUtils;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;

// TODO some kind of context of why the method was attached (e.g. which plugin declared the rule)
// TODO some kind of instance state for the method (might be the same as context above)
public class MethodModelRuleDescriptor extends AbstractModelRuleDescriptor {

    private static final Joiner PARAMS_JOINER = Joiner.on(", ");
    private static final Function<Type, String> TYPE_TO_STRING = new Function<Type, String>() {
        public String apply(Type input) {
            return TypeToken.of(input).toString();
        }
    };

    private final Method method;
    private String description;

    public MethodModelRuleDescriptor(Method method) {
        this.method = method;
    }

    public void describeTo(Appendable appendable) {
        try {
            appendable.append(getDescription());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String getDescription() {
        if (description == null) {
            StringBuilder sb = new StringBuilder(method.getDeclaringClass().getName());
            sb.append("#");
            sb.append(method.getName());
            sb.append("(");
            PARAMS_JOINER.appendTo(sb, Iterables.transform(Arrays.asList(method.getGenericParameterTypes()), TYPE_TO_STRING));
            sb.append(")");
            description = sb.toString();
        }

        return description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        MethodModelRuleDescriptor that = (MethodModelRuleDescriptor) o;

        return method.equals(that.method);
    }

    @Override
    public int hashCode() {
        return method.hashCode();
    }

    public static ModelRuleDescriptor of(Class<?> clazz, final String methodName) {
        List<Method> methodsOfName = CollectionUtils.filter(clazz.getDeclaredMethods(), new Spec<Method>() {
            public boolean isSatisfiedBy(Method element) {
                return element.getName().equals(methodName);
            }
        });

        if (methodsOfName.isEmpty()) {
            throw new IllegalStateException("Class " + clazz.getName() + " has no method named '" + methodName + "'");
        }

        if (methodsOfName.size() > 1) {
            throw new IllegalStateException("Class " + clazz.getName() + " has more than one method named '" + methodName + "'");
        }

        return new MethodModelRuleDescriptor(methodsOfName.get(0));
    }
}
