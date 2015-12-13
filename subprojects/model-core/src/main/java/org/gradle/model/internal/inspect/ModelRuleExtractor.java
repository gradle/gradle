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

import com.google.common.base.Joiner;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Ordering;
import com.google.common.util.concurrent.UncheckedExecutionException;
import net.jcip.annotations.ThreadSafe;
import org.gradle.api.Nullable;
import org.gradle.api.Transformer;
import org.gradle.internal.Cast;
import org.gradle.internal.UncheckedException;
import org.gradle.model.InvalidModelRuleDeclarationException;
import org.gradle.model.RuleSource;
import org.gradle.model.internal.core.ExtractedModelRule;
import org.gradle.model.internal.type.ModelType;
import org.gradle.util.CollectionUtils;

import java.lang.reflect.*;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;

@ThreadSafe
public class ModelRuleExtractor {
    private final LoadingCache<Class<?>, RuleSourceSchema<?>> cache = CacheBuilder.newBuilder()
            .weakKeys()
            .build(new CacheLoader<Class<?>, RuleSourceSchema<?>>() {
                public RuleSourceSchema<?> load(Class<?> source) {
                    return doExtract(source);
                }
            });

    private final Iterable<MethodModelRuleExtractor> handlers;

    public ModelRuleExtractor(Iterable<MethodModelRuleExtractor> handlers) {
        this.handlers = handlers;
    }

    private String describeHandlers() {
        String desc = Joiner.on(", ").join(CollectionUtils.collect(handlers, new Transformer<String, MethodModelRuleExtractor>() {
            public String transform(MethodModelRuleExtractor original) {
                return original.getDescription();
            }
        }));

        return "[" + desc + "]";
    }

    public <T> RuleSourceSchema<T> extract(Class<T> source) throws InvalidModelRuleDeclarationException {
        try {
            return Cast.uncheckedCast(cache.get(source));
        } catch (ExecutionException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        } catch (UncheckedExecutionException e) {
            throw UncheckedException.throwAsUncheckedException(e.getCause());
        }
    }

    private <T> RuleSourceSchema<T> doExtract(Class<T> source) {
        DefaultMethodModelRuleExtractionContext context = new DefaultMethodModelRuleExtractionContext(source, this);

        // TODO - exceptions thrown here should point to some extensive documentation on the concept of class rule sources

        validateClass(source, context);
        final Method[] methods = source.getDeclaredMethods();

        // sort for determinism
        Arrays.sort(methods, Ordering.usingToString());

        ImmutableList.Builder<ExtractedModelRule> registrations = ImmutableList.builder();

        for (Method method : methods) {
            MethodRuleDefinition<?, ?> ruleDefinition = DefaultMethodRuleDefinition.create(source, method);
            ExtractedModelRule registration = getMethodHandler(ruleDefinition, method, context);
            if (registration != null) {
                registrations.add(registration);
            }
        }

        if (context.hasProblems()) {
            throw new InvalidModelRuleDeclarationException(context.problems.format());
        }

        return new RuleSourceSchema<T>(ModelType.of(source), registrations.build());
    }

    @Nullable
    private ExtractedModelRule getMethodHandler(MethodRuleDefinition<?, ?> ruleDefinition, Method method, MethodModelRuleExtractionContext context) {
        MethodModelRuleExtractor handler = null;
        for (MethodModelRuleExtractor candidateHandler : handlers) {
            if (candidateHandler.isSatisfiedBy(ruleDefinition)) {
                if (handler == null) {
                    handler = candidateHandler;
                } else {
                    context.add(method, "Can only be one of " + describeHandlers());
                    validateRuleMethod(method, context);
                    return null;
                }
            }
        }
        if (handler != null) {
            validateRuleMethod(method, context);
            return handler.registration(ruleDefinition, context);
        } else {
            validateNonRuleMethod(method, context);
            return null;
        }
    }

    private void validateClass(Class<?> source, ValidationProblemCollector problems) {
        int modifiers = source.getModifiers();

        if (Modifier.isInterface(modifiers)) {
            problems.add("Must be a class, not an interface");
        }

        if (!RuleSource.class.isAssignableFrom(source) || !source.getSuperclass().equals(RuleSource.class)) {
            problems.add("Rule source classes must directly extend " + RuleSource.class.getName());
        }

        if (Modifier.isAbstract(modifiers)) {
            problems.add("Class cannot be abstract");
        }

        if (source.getEnclosingClass() != null) {
            if (Modifier.isStatic(modifiers)) {
                if (Modifier.isPrivate(modifiers)) {
                    problems.add("Class cannot be private");
                }
            } else {
                problems.add("Enclosed classes must be static and non private");
            }
        }

        Constructor<?>[] constructors = source.getDeclaredConstructors();
        for (Constructor<?> constructor : constructors) {
            if (constructor.getParameterTypes().length > 0) {
                problems.add("Cannot declare a constructor that takes arguments");
                break;
            }
        }

        Field[] fields = source.getDeclaredFields();
        for (Field field : fields) {
            int fieldModifiers = field.getModifiers();
            if (!field.isSynthetic() && !(Modifier.isStatic(fieldModifiers) && Modifier.isFinal(fieldModifiers))) {
                problems.add("Field " + field.getName() + " is not static final");
            }
        }
    }

    private void validateRuleMethod(Method ruleMethod, ValidationProblemCollector problems) {
        if (Modifier.isPrivate(ruleMethod.getModifiers())) {
            problems.add(ruleMethod, "A rule method cannot be private");
        }

        if (ruleMethod.getTypeParameters().length > 0) {
            problems.add(ruleMethod, "Cannot have type variables (i.e. cannot be a generic method)");
        }

        // TODO validations on method: synthetic, bridge methods, varargs, abstract, native
        ModelType<?> returnType = ModelType.returnType(ruleMethod);
        if (returnType.isRawClassOfParameterizedType()) {
            problems.add(ruleMethod, "Raw type " + returnType + " used for return type (all type parameters must be specified of parameterized type)");
        }

        int i = 0;
        for (Type type : ruleMethod.getGenericParameterTypes()) {
            ++i;
            ModelType<?> modelType = ModelType.of(type);
            if (modelType.isRawClassOfParameterizedType()) {
                problems.add(ruleMethod, "Raw type " + modelType + " used for parameter " + i + " (all type parameters must be specified of parameterized type)");
            }
        }
    }

    private void validateNonRuleMethod(Method method, ValidationProblemCollector problems) {
        if (!Modifier.isPrivate(method.getModifiers()) && !Modifier.isStatic(method.getModifiers()) && !method.isSynthetic()) {
            problems.add(method, "A method that is not annotated as a rule must be private");
        }
    }

}
