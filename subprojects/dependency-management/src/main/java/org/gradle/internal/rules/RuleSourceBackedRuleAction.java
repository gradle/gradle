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

package org.gradle.internal.rules;

import com.google.common.collect.Lists;
import org.gradle.api.specs.Spec;
import org.gradle.internal.reflect.JavaMethod;
import org.gradle.internal.reflect.JavaReflectionUtil;
import org.gradle.model.InvalidModelRuleDeclarationException;
import org.gradle.model.Mutate;
import org.gradle.model.internal.core.ModelType;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;

public class RuleSourceBackedRuleAction<R, T> implements RuleAction<T> {
    private final R instance;
    private final JavaMethod<R, T> ruleMethod;

    private RuleSourceBackedRuleAction(R instance, JavaMethod<R, T> ruleMethod) {
        this.instance = instance;
        this.ruleMethod = ruleMethod;
    }

    public static <R, T> RuleSourceBackedRuleAction<R, T> create(ModelType<T> subjectType, R ruleSourceInstance) {
        ModelType<R> ruleSourceType = ModelType.typeOf(ruleSourceInstance);
        List<Method> mutateMethods = JavaReflectionUtil.findAllMethods(ruleSourceType.getConcreteClass(), new Spec<Method>() {
            public boolean isSatisfiedBy(Method element) {
                return element.isAnnotationPresent(Mutate.class);
            }
        });
        if (mutateMethods.size() != 1) {
            throw invalid(ruleSourceType, "must have at exactly one method annotated with @Mutate");
        }
        Method ruleMethod = mutateMethods.get(0);
        if (ruleMethod.getReturnType() != Void.TYPE) {
            throw invalid(ruleSourceType, "rule method must return void");
        }
        Type[] parameterTypes = ruleMethod.getGenericParameterTypes();
        if (parameterTypes.length == 0) {
            throw invalid(ruleSourceType, "rule method must have at least one parameter");
        }
        if (!subjectType.isAssignableFrom(ModelType.of(parameterTypes[0]))) {
            throw invalid(ruleSourceType, String.format("first parameter of rule method must be of type %s", subjectType));
        }
        return new RuleSourceBackedRuleAction<R, T>(ruleSourceInstance, new JavaMethod<R, T>(ruleSourceType.getConcreteClass(), subjectType.getConcreteClass(), ruleMethod));
    }

    private static RuntimeException invalid(ModelType<?> source, String reason) {
        return new InvalidModelRuleDeclarationException("Type " + source + " is not a valid model rule source: " + reason);
    }

    public List<Class<?>> getInputTypes() {
        Class<?>[] parameterTypes = ruleMethod.getParameterTypes();
        return Arrays.asList(parameterTypes).subList(1, parameterTypes.length);
    }

    public void execute(T subject, List<?> inputs) {
        List<Object> args = Lists.newArrayList();
        args.add(subject);
        args.addAll(inputs);
        ruleMethod.invoke(instance, args.toArray());
    }
}
