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
import com.google.common.util.concurrent.UncheckedExecutionException;
import net.jcip.annotations.ThreadSafe;
import org.gradle.api.Transformer;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.reflect.MethodDescription;
import org.gradle.model.InvalidModelRuleDeclarationException;
import org.gradle.model.RuleSource;
import org.gradle.model.internal.core.ExtractedModelRule;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.type.ModelType;
import org.gradle.util.CollectionUtils;

import java.lang.reflect.*;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;

@ThreadSafe
public class ModelRuleExtractor {

    final LoadingCache<Class<?>, List<ExtractedModelRule>> cache = CacheBuilder.newBuilder()
            .weakKeys()
            .build(new CacheLoader<Class<?>, List<ExtractedModelRule>>() {
                public List<ExtractedModelRule> load(Class<?> source) throws Exception {
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

    private static RuntimeException invalid(Class<?> source, String reason) {
        return invalid(source, reason, null);
    }

    private static RuntimeException invalid(Class<?> source, String reason, Throwable throwable) {
        return new InvalidModelRuleDeclarationException("Type " + source.getName() + " is not a valid model rule source: " + reason, throwable);
    }

    private static RuntimeException invalidMethod(Method method, String reason) {
        String description = MethodDescription.name(method.getName())
                .owner(method.getDeclaringClass())
                .takes(method.getGenericParameterTypes())
                .toString();
        return invalid(description, reason);
    }

    private static RuntimeException invalid(ModelRuleDescriptor rule, String reason) {
        StringBuilder sb = new StringBuilder();
        rule.describeTo(sb);
        return invalid(sb.toString(), reason);
    }

    private static RuntimeException invalid(String ruleDescription, String reason) {
        StringBuilder sb = new StringBuilder();
        sb.append(ruleDescription).append(" is not a valid model rule method").append(": ").append(reason);
        return new InvalidModelRuleDeclarationException(sb.toString());
    }

    public Iterable<ExtractedModelRule> extract(Class<?> source) {
        try {
            return cache.get(source);
        } catch (ExecutionException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        } catch (UncheckedExecutionException e) {
            throw UncheckedException.throwAsUncheckedException(e.getCause());
        }
    }

    private List<ExtractedModelRule> doExtract(Class<?> source) {
        validate(source);
        final Method[] methods = source.getDeclaredMethods();

        // sort for determinism
        Arrays.sort(methods, new Comparator<Method>() {
            public int compare(Method o1, Method o2) {
                return o1.toString().compareTo(o2.toString());
            }
        });

        ImmutableList.Builder<ExtractedModelRule> registrations = ImmutableList.builder();

        for (Method method : methods) {
            if (method.getTypeParameters().length > 0) {
                throw invalidMethod(method, "cannot have type variables (i.e. cannot be a generic method)");
            }

            MethodRuleDefinition<?, ?> ruleDefinition = DefaultMethodRuleDefinition.create(source, method);
            MethodModelRuleExtractor handler = getMethodHandler(ruleDefinition);
            if (handler != null) {
                validateMethod(method);
                ExtractedModelRule registration = handler.registration(ruleDefinition);
                if (registration != null) {
                    registrations.add(registration);
                }
            }
        }
        return registrations.build();
    }

    private MethodModelRuleExtractor getMethodHandler(MethodRuleDefinition<?, ?> ruleDefinition) {
        MethodModelRuleExtractor handler = null;
        for (MethodModelRuleExtractor candidateHandler : handlers) {
            if (candidateHandler.getSpec().isSatisfiedBy(ruleDefinition)) {
                if (handler == null) {
                    handler = candidateHandler;
                } else {
                    throw invalid(ruleDefinition.getDescriptor(), "can only be one of " + describeHandlers());
                }
            }
        }
        return handler;
    }

    /**
     * Validates that the given class is effectively static and has no instance state.
     *
     * @param source the class the validate
     */
    public void validate(Class<?> source) throws InvalidModelRuleDeclarationException {
        // TODO - exceptions thrown here should point to some extensive documentation on the concept of class rule sources

        int modifiers = source.getModifiers();

        if (Modifier.isInterface(modifiers)) {
            throw invalid(source, "must be a class, not an interface");
        }

        if (!RuleSource.class.isAssignableFrom(source) || !source.getSuperclass().equals(RuleSource.class)) {
            throw invalid(source, "rule source classes must directly extend " + RuleSource.class.getName());
        }

        if (Modifier.isAbstract(modifiers)) {
            throw invalid(source, "class cannot be abstract");
        }

        if (source.getEnclosingClass() != null) {
            if (Modifier.isStatic(modifiers)) {
                if (Modifier.isPrivate(modifiers)) {
                    throw invalid(source, "class cannot be private");
                }
            } else {
                throw invalid(source, "enclosed classes must be static and non private");
            }
        }

        Constructor<?>[] constructors = source.getDeclaredConstructors();
        for (Constructor<?> constructor : constructors) {
            if (constructor.getParameterTypes().length > 0) {
                throw invalid(source, "cannot declare a constructor that takes arguments");
            }
        }

        try {
            Constructor<?> constructor = constructors[0];
            constructor.setAccessible(true);
            constructor.newInstance();
        } catch (InvocationTargetException e) {
            throw invalid(source, "instance creation failed", e.getCause());
        } catch (InstantiationException e) {
            throw invalid(source, "instance creation failed", e);
        } catch (IllegalAccessException e) {
            throw invalid(source, "must have an accessible constructor", e);
        }

        Field[] fields = source.getDeclaredFields();
        for (Field field : fields) {
            int fieldModifiers = field.getModifiers();
            if (!field.isSynthetic() && !(Modifier.isStatic(fieldModifiers) && Modifier.isFinal(fieldModifiers))) {
                throw invalid(source, "field " + field.getName() + " is not static final");
            }
        }
    }

    private void validateMethod(Method ruleMethod) {
        // TODO validations on method: synthetic, bridge methods, varargs, abstract, native
        ModelType<?> returnType = ModelType.returnType(ruleMethod);
        if (returnType.isRawClassOfParameterizedType()) {
            throw invalidMethod(ruleMethod, "raw type " + returnType + " used for return type (all type parameters must be specified of parameterized type)");
        }

        int i = 0;
        for (Type type : ruleMethod.getGenericParameterTypes()) {
            ++i;
            ModelType<?> modelType = ModelType.of(type);
            if (modelType.isRawClassOfParameterizedType()) {
                throw invalidMethod(ruleMethod, "raw type " + modelType + " used for parameter " + i + " (all type parameters must be specified of parameterized type)");
            }
        }
    }

}
