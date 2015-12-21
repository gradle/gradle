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
import org.gradle.internal.Factory;
import org.gradle.internal.UncheckedException;
import org.gradle.model.InvalidModelRuleDeclarationException;
import org.gradle.model.RuleSource;
import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.core.ModelReference;
import org.gradle.model.internal.manage.instance.GeneratedViewState;
import org.gradle.model.internal.manage.instance.ManagedProxyFactory;
import org.gradle.model.internal.manage.schema.ModelProperty;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.manage.schema.StructSchema;
import org.gradle.model.internal.manage.schema.extract.ModelSchemaUtils;
import org.gradle.model.internal.method.WeaklyTypeReferencingMethod;
import org.gradle.model.internal.type.ModelType;
import org.gradle.util.CollectionUtils;

import java.lang.reflect.*;
import java.util.*;
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
    private final ManagedProxyFactory proxyFactory;
    private final ModelSchemaStore schemaStore;

    public ModelRuleExtractor(Iterable<MethodModelRuleExtractor> handlers, ManagedProxyFactory proxyFactory, ModelSchemaStore schemaStore) {
        this.handlers = handlers;
        this.proxyFactory = proxyFactory;
        this.schemaStore = schemaStore;
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

    private <T> RuleSourceSchema<T> doExtract(final Class<T> source) {
        final ModelType<T> type = ModelType.of(source);
        DefaultMethodModelRuleExtractionContext context = new DefaultMethodModelRuleExtractionContext(type, this);

        // TODO - exceptions thrown here should point to some extensive documentation on the concept of class rule sources

        StructSchema<T> schema = getSchema(source, context);
        if (schema == null) {
            throw new InvalidModelRuleDeclarationException(context.problems.format());
        }
        Factory<T> factory = Modifier.isAbstract(source.getModifiers()) ? new AbstractRuleSourceFactory<T>(schema, proxyFactory) : new ConcreteRuleSourceFactory<T>(type);

        // sort for determinism
        Set<Method> methods = new TreeSet<Method>(Ordering.usingToString());
        methods.addAll(Arrays.asList(source.getDeclaredMethods()));

        for (ModelProperty<?> property : schema.getProperties()) {
            for (WeaklyTypeReferencingMethod<?, ?> method : property.getGetters()) {
                methods.remove(method.getMethod());
            }
            if (property.getSetter() != null) {
                methods.remove(property.getSetter().getMethod());
            }
        }

        ImmutableList.Builder<ExtractedModelRule> registrations = ImmutableList.builder();
        for (Method method : methods) {
            MethodRuleDefinition<?, ?> ruleDefinition = DefaultMethodRuleDefinition.create(source, method, factory);
            ExtractedModelRule registration = getMethodHandler(ruleDefinition, method, context);
            if (registration != null) {
                registrations.add(registration);
            }
        }

        if (context.hasProblems()) {
            throw new InvalidModelRuleDeclarationException(context.problems.format());
        }

        return new RuleSourceSchema<T>(type, registrations.build(), factory);
    }

    private <T> StructSchema<T> getSchema(Class<T> source, DefaultMethodModelRuleExtractionContext context) {
        if (!RuleSource.class.isAssignableFrom(source) || !source.getSuperclass().equals(RuleSource.class)) {
            context.add("Rule source classes must directly extend " + RuleSource.class.getName());
        }

        ModelSchema<T> schema = schemaStore.getSchema(source);
        if (!(schema instanceof StructSchema)) {
            return null;
        }

        validateClass(source, context);
        return (StructSchema<T>) schema;
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
                    validateRuleMethod(ruleDefinition, method, context);
                    return null;
                }
            }
        }
        if (handler != null) {
            validateRuleMethod(ruleDefinition, method, context);
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
                problems.add(field, "Fields must be static final.");
            }
        }
    }

    private void validateRuleMethod(MethodRuleDefinition<?, ?> ruleDefinition, Method ruleMethod, ValidationProblemCollector problems) {
        if (Modifier.isPrivate(ruleMethod.getModifiers())) {
            problems.add(ruleMethod, "A rule method cannot be private");
        }
        if (Modifier.isAbstract(ruleMethod.getModifiers())) {
            problems.add(ruleMethod, "A rule method cannot be abstract");
        }

        if (ruleMethod.getTypeParameters().length > 0) {
            problems.add(ruleMethod, "Cannot have type variables (i.e. cannot be a generic method)");
        }

        // TODO validations on method: synthetic, bridge methods, varargs, abstract, native
        ModelType<?> returnType = ModelType.returnType(ruleMethod);
        if (returnType.isRawClassOfParameterizedType()) {
            problems.add(ruleMethod, "Raw type " + returnType + " used for return type (all type parameters must be specified of parameterized type)");
        }

        for (int i = 0; i < ruleDefinition.getReferences().size(); i++) {
            ModelReference<?> reference = ruleDefinition.getReferences().get(i);
            if (reference.getType().isRawClassOfParameterizedType()) {
                problems.add(ruleMethod, "Raw type " + reference.getType() + " used for parameter " + (i+1) + " (all type parameters must be specified of parameterized type)");
            }
            if (reference.getPath() != null) {
                try {
                    ModelPath.validatePath(reference.getPath().toString());
                } catch (Exception e) {
                    problems.add(ruleDefinition, "The declared model element path '" + reference.getPath() + "' used for parameter " + (i+1) + " is not a valid path", e);
                }
            }
        }
    }

    private void validateNonRuleMethod(Method method, ValidationProblemCollector problems) {
        if (!Modifier.isPrivate(method.getModifiers()) && !Modifier.isStatic(method.getModifiers()) && !method.isSynthetic() && !ModelSchemaUtils.isObjectMethod(method)) {
            problems.add(method, "A method that is not annotated as a rule must be private");
        }
    }

    private static class ConcreteRuleSourceFactory<T> implements Factory<T> {
        // Reference class via `ModelType` to avoid strong reference
        private final ModelType<T> type;

        public ConcreteRuleSourceFactory(ModelType<T> type) {
            this.type = type;
        }

        @Override
        public T create() {
            Class<T> concreteClass = type.getConcreteClass();
            try {
                Constructor<T> declaredConstructor = concreteClass.getDeclaredConstructor();
                declaredConstructor.setAccessible(true);
                return declaredConstructor.newInstance();
            } catch (InvocationTargetException e) {
                throw UncheckedException.throwAsUncheckedException(e.getTargetException());
            } catch (Exception e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }
    }

    private static class AbstractRuleSourceFactory<T> implements Factory<T> {
        private final StructSchema<T> schema;
        private final ManagedProxyFactory proxyFactory;

        public AbstractRuleSourceFactory(StructSchema<T> schema, ManagedProxyFactory proxyFactory) {
            this.schema = schema;
            this.proxyFactory = proxyFactory;
        }

        @Override
        public T create() {
            return proxyFactory.createProxy(new GeneratedViewState() {
                Map<String, Object> values = new HashMap<String, Object>();

                @Override
                public String getDisplayName() {
                    return "rule source " + schema.getType().getDisplayName();
                }

                @Override
                public Object get(String name) {
                    return values.get(name);
                }

                @Override
                public void set(String name, Object value) {
                    values.put(name, value);
                }
            }, schema);
        }
    }
}
