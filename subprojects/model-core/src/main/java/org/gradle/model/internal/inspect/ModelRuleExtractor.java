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
import org.gradle.api.Transformer;
import org.gradle.internal.Cast;
import org.gradle.internal.Factory;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.reflect.GroovyMethods;
import org.gradle.model.InvalidModelRuleDeclarationException;
import org.gradle.model.RuleInput;
import org.gradle.model.RuleSource;
import org.gradle.model.RuleTarget;
import org.gradle.model.internal.core.ModelAction;
import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.core.ModelReference;
import org.gradle.model.internal.core.ModelView;
import org.gradle.model.internal.core.MutableModelNode;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.manage.binding.StructBindings;
import org.gradle.model.internal.manage.binding.StructBindingsStore;
import org.gradle.model.internal.manage.instance.GeneratedViewState;
import org.gradle.model.internal.manage.instance.ManagedInstance;
import org.gradle.model.internal.manage.instance.ManagedProxyFactory;
import org.gradle.model.internal.manage.schema.ModelProperty;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.manage.schema.ScalarValueSchema;
import org.gradle.model.internal.manage.schema.StructSchema;
import org.gradle.model.internal.method.WeaklyTypeReferencingMethod;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.model.internal.registry.RuleContext;
import org.gradle.model.internal.type.ModelType;
import org.gradle.util.CollectionUtils;

import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;

@ThreadSafe
public class ModelRuleExtractor {
    private final LoadingCache<Class<?>, CachedRuleSource> cache = CacheBuilder.newBuilder()
            .weakKeys()
            .build(new CacheLoader<Class<?>, CachedRuleSource>() {
                public CachedRuleSource load(Class<?> source) {
                    return doExtract(source);
                }
            });

    private final Iterable<MethodModelRuleExtractor> handlers;
    private final ManagedProxyFactory proxyFactory;
    private final ModelSchemaStore schemaStore;
    private final StructBindingsStore structBindingsStore;

    public ModelRuleExtractor(Iterable<MethodModelRuleExtractor> handlers, ManagedProxyFactory proxyFactory, ModelSchemaStore schemaStore, StructBindingsStore structBindingsStore) {
        this.handlers = handlers;
        this.proxyFactory = proxyFactory;
        this.schemaStore = schemaStore;
        this.structBindingsStore = structBindingsStore;
    }

    private String describeHandlers() {
        String desc = Joiner.on(", ").join(CollectionUtils.collect(handlers, new Transformer<String, MethodModelRuleExtractor>() {
            public String transform(MethodModelRuleExtractor original) {
                return original.getDescription();
            }
        }));
        return "[" + desc + "]";
    }

    /**
     * Creates a new rule source instance to be applied to a model element.
     *
     * @throws InvalidModelRuleDeclarationException On badly formed rule source class.
     */
    public <T> ExtractedRuleSource<T> extract(Class<T> source) throws InvalidModelRuleDeclarationException {
        try {
            return cache.get(source).newInstance(source);
        } catch (ExecutionException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        } catch (UncheckedExecutionException e) {
            throw UncheckedException.throwAsUncheckedException(e.getCause());
        }
    }

    private <T> CachedRuleSource doExtract(final Class<T> source) {
        final ModelType<T> type = ModelType.of(source);
        FormattingValidationProblemCollector problems = new FormattingValidationProblemCollector("rule source", type);
        DefaultMethodModelRuleExtractionContext context = new DefaultMethodModelRuleExtractionContext(this, problems);

        // TODO - exceptions thrown here should point to some extensive documentation on the concept of class rule sources

        StructSchema<T> schema = getSchema(source, context);
        if (schema == null) {
            throw new InvalidModelRuleDeclarationException(problems.format());
        }

        // sort for determinism
        Set<Method> methods = new TreeSet<Method>(Ordering.usingToString());
        methods.addAll(Arrays.asList(source.getDeclaredMethods()));

        ImmutableList.Builder<ModelProperty<?>> implicitInputs = ImmutableList.builder();
        ModelProperty<?> target = null;
        for (ModelProperty<?> property : schema.getProperties()) {
            if (property.isAnnotationPresent(RuleTarget.class)) {
                target = property;
            } else if (property.isAnnotationPresent(RuleInput.class) && !(property.getSchema() instanceof ScalarValueSchema)) {
                implicitInputs.add(property);
            }
            for (WeaklyTypeReferencingMethod<?, ?> method : property.getAccessors()) {
                methods.remove(method.getMethod());
            }
        }

        ImmutableList.Builder<ExtractedRuleDetails> rules = ImmutableList.builder();
        for (Method method : methods) {
            MethodRuleDefinition<?, ?> ruleDefinition = DefaultMethodRuleDefinition.create(source, method);
            ExtractedModelRule rule = getMethodHandler(ruleDefinition, method, context);
            if (rule != null) {
                rules.add(new ExtractedRuleDetails(ruleDefinition, rule));
            }
        }

        if (context.hasProblems()) {
            throw new InvalidModelRuleDeclarationException(problems.format());
        }

        StructBindings<T> bindings = structBindingsStore.getBindings(schema.getType());

        if (schema.getProperties().isEmpty()) {
            return new StatelessRuleSource(rules.build(), Modifier.isAbstract(source.getModifiers()) ? new AbstractRuleSourceFactory<T>(schema, bindings, proxyFactory) : new ConcreteRuleSourceFactory<T>(type));
        } else {
            return new ParameterizedRuleSource(rules.build(), target, implicitInputs.build(), schema, bindings, proxyFactory);
        }
    }

    private <T> StructSchema<T> getSchema(Class<T> source, RuleSourceValidationProblemCollector problems) {
        if (!RuleSource.class.isAssignableFrom(source) || !source.getSuperclass().equals(RuleSource.class)) {
            problems.add("Rule source classes must directly extend " + RuleSource.class.getName());
        }

        ModelSchema<T> schema = schemaStore.getSchema(source);
        if (!(schema instanceof StructSchema)) {
            return null;
        }

        validateClass(source, problems);
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

    private void validateClass(Class<?> source, RuleSourceValidationProblemCollector problems) {
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

    private void validateRuleMethod(MethodRuleDefinition<?, ?> ruleDefinition, Method ruleMethod, RuleSourceValidationProblemCollector problems) {
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
                problems.add(ruleMethod, "Raw type " + reference.getType() + " used for parameter " + (i + 1) + " (all type parameters must be specified of parameterized type)");
            }
            if (reference.getPath() != null) {
                try {
                    ModelPath.validatePath(reference.getPath().getPath());
                } catch (Exception e) {
                    problems.add(ruleDefinition, "The declared model element path '" + reference.getPath().getPath() + "' used for parameter " + (i + 1) + " is not a valid path", e);
                }
            }
        }
    }

    private void validateNonRuleMethod(Method method, RuleSourceValidationProblemCollector problems) {
        if (!Modifier.isPrivate(method.getModifiers()) && !Modifier.isStatic(method.getModifiers()) && !method.isSynthetic() && !GroovyMethods.isObjectMethod(method)) {
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

    private static class AbstractRuleSourceFactory<T> implements Factory<T>, GeneratedViewState {
        private final StructSchema<T> schema;
        private final StructBindings<?> bindings;
        private final ManagedProxyFactory proxyFactory;

        public AbstractRuleSourceFactory(StructSchema<T> schema, StructBindings<?> bindings, ManagedProxyFactory proxyFactory) {
            this.schema = schema;
            this.bindings = bindings;
            this.proxyFactory = proxyFactory;
        }

        @Override
        public String getDisplayName() {
            return "rule source " + schema.getType().getDisplayName();
        }

        @Override
        public Object get(String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void set(String name, Object value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public T create() {
            return proxyFactory.createProxy(this, schema, bindings);
        }
    }

    interface CachedRuleSource {
        <T> ExtractedRuleSource<T> newInstance(Class<T> source);
    }

    private static class StatelessRuleSource implements CachedRuleSource {
        private final DefaultExtractedRuleSource<?> ruleSource;

        public <T> StatelessRuleSource(List<ExtractedRuleDetails> rules, Factory<T> factory) {
            this.ruleSource = new StatelessExtractedRuleSource<T>(rules, factory);
        }

        @Override
        public <T> ExtractedRuleSource<T> newInstance(Class<T> source) {
            return Cast.uncheckedCast(ruleSource);
        }
    }

    private static class ParameterizedRuleSource implements CachedRuleSource {
        private final List<ExtractedRuleDetails> rules;
        @Nullable
        private final ModelProperty<?> target;
        private final List<ModelProperty<?>> implicitInputs;
        private final StructSchema<?> schema;
        private final StructBindings<?> bindings;
        private final ManagedProxyFactory proxyFactory;

        public <T> ParameterizedRuleSource(List<ExtractedRuleDetails> rules, @Nullable ModelProperty<?> target, List<ModelProperty<?>> implicitInputs, StructSchema<T> schema, StructBindings<?> bindings, ManagedProxyFactory proxyFactory) {
            this.rules = rules;
            this.target = target;
            this.implicitInputs = implicitInputs;
            this.schema = schema;
            this.bindings = bindings;
            this.proxyFactory = proxyFactory;
        }

        @Override
        public <T> ExtractedRuleSource<T> newInstance(Class<T> source) {
            StructSchema<T> schema = Cast.uncheckedCast(this.schema);
            return new ParameterizedExtractedRuleSource<T>(rules, target, implicitInputs, schema, bindings, proxyFactory);
        }
    }

    private static class ExtractedRuleDetails {
        final MethodRuleDefinition<?, ?> method;
        final ExtractedModelRule rule;

        public ExtractedRuleDetails(MethodRuleDefinition<?, ?> method, ExtractedModelRule rule) {
            this.method = method;
            this.rule = rule;
        }
    }

    private static abstract class DefaultExtractedRuleSource<T> implements ExtractedRuleSource<T> {
        private final List<ExtractedRuleDetails> rules;

        public DefaultExtractedRuleSource(List<ExtractedRuleDetails> rules) {
            this.rules = rules;
        }

        public List<ExtractedModelRule> getRules() {
            return CollectionUtils.collect(rules, new Transformer<ExtractedModelRule, ExtractedRuleDetails>() {
                @Override
                public ExtractedModelRule transform(ExtractedRuleDetails extractedRuleDetails) {
                    return extractedRuleDetails.rule;
                }
            });
        }

        @Override
        public void apply(final ModelRegistry modelRegistry, MutableModelNode target) {
            final ModelPath targetPath = calculateTarget(target);
            for (final ExtractedRuleDetails details : rules) {
                details.rule.apply(new MethodModelRuleApplicationContext() {
                    @Override
                    public ModelRegistry getRegistry() {
                        return modelRegistry;
                    }

                    @Override
                    public ModelPath getScope() {
                        return targetPath;
                    }

                    @Override
                    public ModelAction contextualize(final MethodRuleAction action) {
                        final List<ModelReference<?>> inputs = withImplicitInputs(action.getInputs());
                        final ModelReference<?> mappedSubject = mapSubject(action.getSubject(), targetPath);
                        mapInputs(inputs.subList(0, action.getInputs().size()), targetPath);
                        final MethodRuleDefinition<?, ?> methodRuleDefinition = details.method;
                        final Factory<? extends T> factory = getFactory();
                        return new ContextualizedModelAction<T>(methodRuleDefinition, mappedSubject, inputs, action, factory);
                    }
                }, target);
            }
        }

        protected ModelPath calculateTarget(MutableModelNode target) {
            return target.getPath();
        }

        private void mapInputs(List<ModelReference<?>> inputs, ModelPath targetPath) {
            for (int i = 0; i < inputs.size(); i++) {
                ModelReference<?> input = inputs.get(i);
                if (input.getPath() != null) {
                    inputs.set(i, input.withPath(targetPath.descendant(input.getPath())));
                } else {
                    inputs.set(i, input.inScope(ModelPath.ROOT));
                }
            }
        }

        private ModelReference<?> mapSubject(ModelReference<?> subject, ModelPath targetPath) {
            if (subject.getPath() == null) {
                return subject.inScope(targetPath);
            } else {
                return subject.withPath(targetPath.descendant(subject.getPath()));
            }
        }

        protected List<ModelReference<?>> withImplicitInputs(List<? extends ModelReference<?>> inputs) {
            return new ArrayList<ModelReference<?>>(inputs);
        }

        @Override
        public List<? extends Class<?>> getRequiredPlugins() {
            List<Class<?>> plugins = new ArrayList<Class<?>>();
            for (ExtractedRuleDetails details : rules) {
                plugins.addAll(details.rule.getRuleDependencies());
            }
            return plugins;
        }

        @Override
        public void assertNoPlugins() {
            for (ExtractedRuleDetails details : rules) {
                if (!details.rule.getRuleDependencies().isEmpty()) {
                    StringBuilder message = new StringBuilder();
                    details.method.getDescriptor().describeTo(message);
                    message.append(" has dependencies on plugins: ");
                    message.append(details.rule.getRuleDependencies());
                    message.append(". Plugin dependencies are not supported in this context.");
                    throw new UnsupportedOperationException(message.toString());
                }
            }
        }

        private static class ContextualizedModelAction<T> implements ModelAction {
            private final MethodRuleDefinition<?, ?> methodRuleDefinition;
            private final ModelReference<?> mappedSubject;
            private final List<ModelReference<?>> inputs;
            private final MethodRuleAction action;
            private final Factory<? extends T> factory;

            public ContextualizedModelAction(MethodRuleDefinition<?, ?> methodRuleDefinition, ModelReference<?> mappedSubject, List<ModelReference<?>> inputs, MethodRuleAction action, Factory<? extends T> factory) {
                this.methodRuleDefinition = methodRuleDefinition;
                this.mappedSubject = mappedSubject;
                this.inputs = inputs;
                this.action = action;
                this.factory = factory;
            }

            @Override
            public ModelRuleDescriptor getDescriptor() {
                return methodRuleDefinition.getDescriptor();
            }

            @Override
            public ModelReference<?> getSubject() {
                return mappedSubject;
            }

            @Override
            public List<? extends ModelReference<?>> getInputs() {
                return inputs;
            }

            @Override
            public void execute(MutableModelNode modelNode, List<ModelView<?>> inputs) {
                WeaklyTypeReferencingMethod<Object, Object> method = Cast.uncheckedCast(methodRuleDefinition.getMethod());
                ModelRuleInvoker<Object> invoker = new DefaultModelRuleInvoker<Object, Object>(method, factory);
                action.execute(invoker, modelNode, inputs.subList(0, action.getInputs().size()));
            }
        }
    }

    private static class StatelessExtractedRuleSource<T> extends DefaultExtractedRuleSource<T> {
        private final Factory<T> factory;

        public StatelessExtractedRuleSource(List<ExtractedRuleDetails> rules, Factory<T> factory) {
            super(rules);
            this.factory = factory;
        }

        @Override
        public Factory<? extends T> getFactory() {
            return factory;
        }
    }

    private static class ParameterizedExtractedRuleSource<T> extends DefaultExtractedRuleSource<T> implements GeneratedViewState, Factory<T> {
        @Nullable
        private final ModelProperty<?> targetProperty;
        private final List<ModelProperty<?>> implicitInputs;
        private final StructSchema<T> schema;
        private final StructBindings<?> bindings;
        private final ManagedProxyFactory proxyFactory;
        private final Map<String, Object> values = new HashMap<String, Object>();

        public ParameterizedExtractedRuleSource(List<ExtractedRuleDetails> rules, @Nullable ModelProperty<?> targetProperty, List<ModelProperty<?>> implicitInputs, StructSchema<T> schema, StructBindings<?> bindings, ManagedProxyFactory proxyFactory) {
            super(rules);
            this.targetProperty = targetProperty;
            this.implicitInputs = implicitInputs;
            this.schema = schema;
            this.bindings = bindings;
            this.proxyFactory = proxyFactory;
        }

        @Override
        public Factory<? extends T> getFactory() {
            return this;
        }

        @Override
        public T create() {
            return proxyFactory.createProxy(this, schema, bindings);
        }

        @Override
        public String getDisplayName() {
            return "rule source " + schema.getType().getDisplayName();
        }

        @Override
        public Object get(String name) {
            ModelProperty<?> property = schema.getProperty(name);
            if (property.getSchema() instanceof ScalarValueSchema) {
                return values.get(name);
            }

            MutableModelNode node = (MutableModelNode) values.get(name);
            if (node == null) {
                return null;
            }
            return node.asImmutable(property.getType(), RuleContext.get()).getInstance();
        }

        @Override
        public void set(String name, Object value) {
            ModelProperty<?> property = schema.getProperty(name);
            if (property.getSchema() instanceof ScalarValueSchema) {
                values.put(name, value);
            } else {
                MutableModelNode backingNode = ((ManagedInstance) value).getBackingNode();
                values.put(name, backingNode);
            }
        }

        @Override
        protected ModelPath calculateTarget(MutableModelNode target) {
            if (targetProperty != null) {
                return ((MutableModelNode)values.get(targetProperty.getName())).getPath();
            }
            return target.getPath();
        }

        @Override
        protected List<ModelReference<?>> withImplicitInputs(List<? extends ModelReference<?>> inputs) {
            List<ModelReference<?>> allInputs = new ArrayList<ModelReference<?>>(inputs.size() + implicitInputs.size());
            allInputs.addAll(inputs);
            for (ModelProperty<?> property : implicitInputs) {
                MutableModelNode backingNode = (MutableModelNode) values.get(property.getName());
                allInputs.add(ModelReference.of(backingNode.getPath()));
            }
            return allInputs;
        }
    }
}
