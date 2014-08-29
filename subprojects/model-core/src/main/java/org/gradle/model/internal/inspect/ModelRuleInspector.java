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
import com.google.common.collect.ImmutableSet;
import org.gradle.api.Transformer;
import org.gradle.model.InvalidModelRuleDeclarationException;
import org.gradle.model.RuleSource;
import org.gradle.model.internal.core.rule.describe.MethodModelRuleDescriptor;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.util.CollectionUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Set;

public class ModelRuleInspector {

    private final Iterable<MethodRuleDefinitionHandler> handlers;

    public ModelRuleInspector(Iterable<MethodRuleDefinitionHandler> handlers) {
        this.handlers = handlers;
    }

    private String describeHandlers() {
        String desc = Joiner.on(", ").join(CollectionUtils.collect(handlers, new Transformer<String, MethodRuleDefinitionHandler>() {
            public String transform(MethodRuleDefinitionHandler original) {
                return original.getDescription();
            }
        }));

        return "[" + desc + "]";
    }

    private static RuntimeException invalid(Class<?> source, String reason) {
        return new InvalidModelRuleDeclarationException("Type " + source.getName() + " is not a valid model rule source: " + reason);
    }

    private static RuntimeException invalid(Method method, String reason) {
        return invalid("model rule method", new MethodModelRuleDescriptor(method), reason);
    }

    private static RuntimeException invalid(String description, ModelRuleDescriptor rule, String reason) {
        StringBuilder sb = new StringBuilder();
        rule.describeTo(sb);
        sb.append(" is not a valid ").append(description).append(": ").append(reason);
        return new InvalidModelRuleDeclarationException(sb.toString());
    }

    // TODO return a richer data structure that provides meta data about how the source was found, for use is diagnostics
    public Set<Class<?>> getDeclaredSources(Class<?> container) {
        Class<?>[] declaredClasses = container.getDeclaredClasses();
        if (declaredClasses.length == 0) {
            return Collections.emptySet();
        } else {
            ImmutableSet.Builder<Class<?>> found = ImmutableSet.builder();
            for (Class<?> declaredClass : declaredClasses) {
                if (declaredClass.isAnnotationPresent(RuleSource.class)) {
                    found.add(declaredClass);
                }
            }

            return found.build();
        }
    }

    // TODO should either return the extracted rule, or metadata about the extraction (i.e. for reporting etc.)
    public <T> void inspect(Class<T> source, ModelRegistry modelRegistry, RuleSourceDependencies dependencies) {
        validate(source);
        final Method[] methods = source.getDeclaredMethods();

        // sort for determinism
        Arrays.sort(methods, new Comparator<Method>() {
            public int compare(Method o1, Method o2) {
                return o1.toString().compareTo(o2.toString());
            }
        });

        for (Method method : methods) {
            validate(method);
            MethodRuleDefinition<?> ruleDefinition = DefaultMethodRuleDefinition.create(source, method);
            MethodRuleDefinitionHandler handler = getMethodHandler(ruleDefinition);
            if (handler != null) {
                // TODO catch “strange” exceptions thrown here and wrap with some context on the rule being registered
                // If the thrown exception doesn't provide any “model rule” context, it will be more or less impossible for a user
                // to work out what happened because the stack trace won't reveal any info about which rule was being registered.
                // However, a “wrap everything” strategy doesn't quite work because the thrown exception may already have enough context
                // and do a better job of explaining what went wrong than what we can do at this level.
                handler.register(ruleDefinition, modelRegistry, dependencies);
            }
        }
    }

    private MethodRuleDefinitionHandler getMethodHandler(MethodRuleDefinition<?> ruleDefinition) {
        MethodRuleDefinitionHandler handler = null;
        for (MethodRuleDefinitionHandler candidateHandler : handlers) {
            if (candidateHandler.getSpec().isSatisfiedBy(ruleDefinition)) {
                if (handler == null) {
                    handler = candidateHandler;
                } else {
                    throw invalid("model rule method", ruleDefinition.getDescriptor(), "can only be one of " + describeHandlers());
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

        Class<?> superclass = source.getSuperclass();
        if (!superclass.equals(Object.class)) {
            throw invalid(source, "cannot have superclass");
        }

        Constructor<?>[] constructors = source.getDeclaredConstructors();
        if (constructors.length > 1) {
            throw invalid(source, "cannot have more than one constructor");
        }

        Constructor<?> constructor = constructors[0];
        if (constructor.getParameterTypes().length > 0) {
            throw invalid(source, "constructor cannot take any arguments");
        }

        Field[] fields = source.getDeclaredFields();
        for (Field field : fields) {
            int fieldModifiers = field.getModifiers();
            if (!field.isSynthetic() && !(Modifier.isStatic(fieldModifiers) && Modifier.isFinal(fieldModifiers))) {
                throw invalid(source, "field " + field.getName() + " is not static final");
            }
        }

    }

    private void validate(Method ruleMethod) {
        // TODO validations on method: synthetic, bridge methods, varargs, abstract, native
        if (ruleMethod.getTypeParameters().length > 0) {
            throw invalid(ruleMethod, "cannot have type variables (i.e. cannot be a generic method)");
        }
    }

}
