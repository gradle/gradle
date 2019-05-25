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

package org.gradle.model.dsl.internal;

import groovy.lang.Closure;
import groovy.lang.GroovyObjectSupport;
import groovy.lang.MissingMethodException;
import groovy.lang.MissingPropertyException;
import javax.annotation.concurrent.NotThreadSafe;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.internal.Actions;
import org.gradle.internal.MutableBoolean;
import org.gradle.model.internal.core.ModelActionRole;
import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.core.ModelReference;
import org.gradle.model.internal.core.ModelRegistrations;
import org.gradle.model.internal.core.NoInputsModelAction;
import org.gradle.model.internal.core.NodeInitializer;
import org.gradle.model.internal.core.NodeInitializerRegistry;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.model.internal.type.ModelType;
import org.gradle.util.ClosureBackedAction;

import static org.gradle.model.internal.core.DefaultNodeInitializerRegistry.DEFAULT_REFERENCE;
import static org.gradle.model.internal.core.NodeInitializerContext.forType;

@NotThreadSafe
public class NonTransformedModelDslBacking extends GroovyObjectSupport {

    // TODO include link to documentation giving more explanation of what's going on here.
    public static final String ATTEMPTED_INPUT_SYNTAX_USED_MESSAGE = "$() syntax cannot be used when model {} block is not a top level statement in the script";

    private final ModelPath modelPath;
    private final ModelRegistry modelRegistry;
    private final MutableBoolean executingDsl;

    public NonTransformedModelDslBacking(ModelRegistry modelRegistry) {
        this(new MutableBoolean(), null, modelRegistry);
    }

    private NonTransformedModelDslBacking(MutableBoolean executingDsl, ModelPath modelPath, ModelRegistry modelRegistry) {
        this.executingDsl = executingDsl;
        this.modelPath = modelPath;
        this.modelRegistry = modelRegistry;
    }

    private NonTransformedModelDslBacking getChildPath(String name) {
        ModelPath path = modelPath == null ? ModelPath.path(name) : modelPath.child(name);
        return new NonTransformedModelDslBacking(executingDsl, path, modelRegistry);
    }

    private void registerConfigurationAction(final Closure<?> action) {
        modelRegistry.configure(ModelActionRole.Mutate,
            new NoInputsModelAction<Object>(
                ModelReference.untyped(modelPath),
                new SimpleModelRuleDescriptor("model." + modelPath), new ClosureBackedAction<Object>(action)
            ));
    }

    private <T> void register(Class<T> type, Closure<?> closure) {
        register(type, new ClosureBackedAction<T>(closure));
    }

    private <T> void register(Class<T> type, Action<? super T> action) {
        ModelRuleDescriptor descriptor = new SimpleModelRuleDescriptor("model." + modelPath);
        NodeInitializerRegistry nodeInitializerRegistry = modelRegistry.realize(DEFAULT_REFERENCE.getPath(), DEFAULT_REFERENCE.getType());
        ModelType<T> modelType = ModelType.of(type);
        NodeInitializer nodeInitializer = nodeInitializerRegistry.getNodeInitializer(forType(modelType));
        modelRegistry.register(
            ModelRegistrations.of(modelPath, nodeInitializer)
                .descriptor(descriptor)
                .action(ModelActionRole.Initialize, NoInputsModelAction.of(ModelReference.of(modelPath, modelType), descriptor, action))
                .build()
        );
    }

    public void configure(Closure<?> action) {
        executingDsl.set(true);
        try {
            ClosureBackedAction.execute(this, action);
        } finally {
            executingDsl.set(false);
        }
    }

    public NonTransformedModelDslBacking propertyMissing(String name) {
        if (!executingDsl.get()) {
            throw new MissingPropertyException(name, getClass());
        }
        return getChildPath(name);
    }

    public Void methodMissing(String name, Object argsObj) {
        Object[] args = (Object[]) argsObj;

        if (!executingDsl.get()) {
            if (name.equals("$")) {
                throw new GradleException(ATTEMPTED_INPUT_SYNTAX_USED_MESSAGE);
            } else {
                throw new MissingMethodException(name, getClass(), args);
            }
        } else {
            if (args.length == 1 && args[0] instanceof Closure) {
                Closure<?> closure = (Closure) args[0];
                getChildPath(name).registerConfigurationAction(closure);
                return null;
            } else if (args.length == 2 && args[0] instanceof Class && args[1] instanceof Closure) {
                Class<?> clazz = (Class<?>) args[0];
                Closure<?> closure = (Closure<?>) args[1];
                getChildPath(name).register(clazz, closure);
                return null;
            } else if (args.length == 1 && args[0] instanceof Class) {
                Class<?> clazz = (Class<?>) args[0];
                getChildPath(name).register(clazz, Actions.doNothing());
                return null;
            } else {
                throw new MissingMethodException(name, getClass(), args);
            }
        }
    }

}
