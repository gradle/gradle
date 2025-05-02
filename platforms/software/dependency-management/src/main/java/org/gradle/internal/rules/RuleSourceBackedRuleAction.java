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

import org.gradle.api.specs.Spec;
import org.gradle.internal.reflect.JavaMethod;
import org.gradle.model.Mutate;
import org.gradle.model.internal.inspect.DefaultRuleSourceValidationProblemCollector;
import org.gradle.model.internal.inspect.FormattingValidationProblemCollector;
import org.gradle.model.internal.inspect.RuleSourceValidationProblemCollector;
import org.gradle.model.internal.type.ModelType;
import org.gradle.util.internal.CollectionUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
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
        List<Method> mutateMethods = findAllMethods(ruleSourceType.getConcreteClass(), element -> element.isAnnotationPresent(Mutate.class));
        FormattingValidationProblemCollector problemsFormatter = new FormattingValidationProblemCollector("rule source", ruleSourceType);
        RuleSourceValidationProblemCollector problems = new DefaultRuleSourceValidationProblemCollector(problemsFormatter);

        if (mutateMethods.size() == 0) {
            problems.add("Must have at exactly one method annotated with @" + Mutate.class.getName());
        } else {
            if (mutateMethods.size() > 1) {
                problems.add("More than one method is annotated with @" + Mutate.class.getName());
            }

            for (Method ruleMethod : mutateMethods) {
                if (ruleMethod.getReturnType() != Void.TYPE) {
                    problems.add(ruleMethod, "A rule method must return void");
                }
                Type[] parameterTypes = ruleMethod.getGenericParameterTypes();
                if (parameterTypes.length == 0 || !subjectType.isAssignableFrom(ModelType.of(parameterTypes[0]))) {
                    problems.add(ruleMethod, String.format("First parameter of a rule method must be of type %s", subjectType));
                }
            }
        }

        if (problemsFormatter.hasProblems()) {
            throw new RuleActionValidationException(problemsFormatter.format());
        }

        return new RuleSourceBackedRuleAction<>(ruleSourceInstance, new JavaMethod<>(subjectType.getConcreteClass(), mutateMethods.get(0)));
    }

    public static List<Class<?>> determineInputTypes(Class<?>[] parameterTypes) {
        return Arrays.asList(parameterTypes).subList(1, parameterTypes.length);
    }

    @Override
    public List<Class<?>> getInputTypes() {
        return inputTypes;
    }

    @Override
    public void execute(T subject, List<?> inputs) {
        Object[] args = new Object[inputs.size() + 1];
        args[0] = subject;
        for (int i = 0; i < inputs.size(); i++) {
            Object input =  inputs.get(i);
            args[i+1] = input;
        }
        ruleMethod.invoke(instance, args);
    }

    private static List<Method> findAllMethods(Class<?> target, Spec<Method> predicate) {
        return findAllMethodsInternal(target, predicate, new MultiMap<>(), new ArrayList<>(), false);
    }

    private static class MultiMap<K, V> extends HashMap<K, List<V>> {
        @Override
        public List<V> get(Object key) {
            if (!containsKey(key)) {
                @SuppressWarnings("unchecked") K keyCast = (K) key;
                put(keyCast, new LinkedList<>());
            }

            return super.get(key);
        }
    }

    private static List<Method> findAllMethodsInternal(Class<?> target, Spec<Method> predicate, MultiMap<String, Method> seen, List<Method> collector, boolean stopAtFirst) {
        for (final Method method : target.getDeclaredMethods()) {
            List<Method> seenWithName = seen.get(method.getName());
            Method override = CollectionUtils.findFirst(seenWithName, potentionOverride -> potentionOverride.getName().equals(method.getName())
                && Arrays.equals(potentionOverride.getParameterTypes(), method.getParameterTypes()));


            if (override == null) {
                seenWithName.add(method);
                if (predicate.isSatisfiedBy(method)) {
                    collector.add(method);
                    if (stopAtFirst) {
                        return collector;
                    }
                }
            }
        }

        Class<?> parent = target.getSuperclass();
        if (parent != null) {
            return findAllMethodsInternal(parent, predicate, seen, collector, stopAtFirst);
        }

        return collector;
    }
}
