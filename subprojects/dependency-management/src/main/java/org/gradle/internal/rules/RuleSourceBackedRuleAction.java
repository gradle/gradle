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
import org.gradle.model.internal.type.ModelType;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;

public class RuleSourceBackedRuleAction<R, T> implements RuleAction<T> {
    private final R instance;
    private final JavaMethod<R, T> ruleMethod;
    private final List<Class<?>> inputTypes;

    private RuleSourceBackedRuleAction(R instance, JavaMethod<R, T> ruleMethod) {
        this.instance = instance;
        this.ruleMethod = ruleMethod;
        this.inputTypes = determineInputTypes(ruleMethod.getParameterTypes());
    }

    public static <R, T> RuleSourceBackedRuleAction<R, T> create(ModelType<T> subjectType, R ruleSourceInstance) {
        ModelType<R> ruleSourceType = ModelType.typeOf(ruleSourceInstance);
        List<Method> mutateMethods = JavaReflectionUtil.findAllMethods(ruleSourceType.getConcreteClass(), new Spec<Method>() {
            public boolean isSatisfiedBy(Method element) {
                return element.isAnnotationPresent(Mutate.class);
            }
        });
        List<String> reasons = Lists.newArrayList();

        if (mutateMethods.size() == 0) {
            reasons.add("must have at exactly one method annotated with @org.gradle.model.Mutate");
        } else {
            if (mutateMethods.size() > 1) {
                reasons.add("more than one method is annotated with @org.gradle.model.Mutate");
            }

            for (Method ruleMethod : mutateMethods) {
                if (ruleMethod.getReturnType() != Void.TYPE) {
                    reasons.add(String.format("rule method '%s' must return void", ruleMethod.getName()));
                }
                Type[] parameterTypes = ruleMethod.getGenericParameterTypes();
                if (parameterTypes.length == 0 || !subjectType.isAssignableFrom(ModelType.of(parameterTypes[0]))) {
                    reasons.add(String.format("first parameter of rule method '%s' must be of type %s", ruleMethod.getName(), subjectType));
                }
            }
        }

        if (reasons.size() > 0) {
            throw invalid(ruleSourceType, reasons);
        }

        return new RuleSourceBackedRuleAction<R, T>(ruleSourceInstance, new JavaMethod<R, T>(ruleSourceType.getConcreteClass(), subjectType.getConcreteClass(), mutateMethods.get(0)));
    }

    private static RuntimeException invalid(ModelType<?> source, List<String> reasons) {
        StringBuilder errorString = new StringBuilder(String.format("Type %s is not a valid model rule source: ", source));
        for (String reason : reasons) {
            errorString.append(String.format("\n- %s", reason));
        }
        return new RuleActionValidationException(null, new InvalidModelRuleDeclarationException(errorString.toString()));
    }

    public static List<Class<?>> determineInputTypes(Class<?>[] parameterTypes) {
        return Arrays.asList(parameterTypes).subList(1, parameterTypes.length);
    }

    public List<Class<?>> getInputTypes() {
        return inputTypes;
    }

    public void execute(T subject, List<?> inputs) {
        Object[] args = new Object[inputs.size() + 1];
        args[0] = subject;
        for (int i = 0; i < inputs.size(); i++) {
            Object input =  inputs.get(i);
            args[i+1] = input;
        }
        ruleMethod.invoke(instance, args);
    }
}
