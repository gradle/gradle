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
import net.jcip.annotations.NotThreadSafe;
import org.gradle.api.GradleException;
import org.gradle.api.internal.ClosureBackedAction;
import org.gradle.model.InvalidModelRuleDeclarationException;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.model.internal.type.ModelType;

import java.util.concurrent.atomic.AtomicBoolean;

@NotThreadSafe
public class NonTransformedModelDslBacking extends GroovyObjectSupport {

    // TODO include link to documentation giving more explanation of what's going on here.
    public static final String ATTEMPTED_INPUT_SYNTAX_USED_MESSAGE = "$() syntax cannot be used when model {} block is not a top level statement in the script";

    private final ModelPath modelPath;
    private final ModelRegistry modelRegistry;
    private final ModelSchemaStore modelSchemaStore;
    private final ModelCreatorFactory modelCreatorFactory;
    private AtomicBoolean executingDsl;

    public NonTransformedModelDslBacking(ModelRegistry modelRegistry, ModelSchemaStore modelSchemaStore, ModelCreatorFactory modelCreatorFactory) {
        this(new AtomicBoolean(), null, modelRegistry, modelSchemaStore, modelCreatorFactory);
    }

    private NonTransformedModelDslBacking(AtomicBoolean executingDsl, ModelPath modelPath, ModelRegistry modelRegistry, ModelSchemaStore modelSchemaStore, ModelCreatorFactory modelCreatorFactory) {
        this.executingDsl = executingDsl;
        this.modelPath = modelPath;
        this.modelRegistry = modelRegistry;
        this.modelSchemaStore = modelSchemaStore;
        this.modelCreatorFactory = modelCreatorFactory;
    }

    private NonTransformedModelDslBacking getChildPath(String name) {
        ModelPath path = modelPath == null ? ModelPath.path(name) : modelPath.child(name);
        return new NonTransformedModelDslBacking(executingDsl, path, modelRegistry, modelSchemaStore, modelCreatorFactory);
    }

    private void registerConfigurationAction(final Closure<?> action) {
        modelRegistry.configure(ModelActionRole.Mutate,
                new ActionBackedModelAction<Object>(
                        ModelReference.untyped(modelPath),
                        new SimpleModelRuleDescriptor("model." + modelPath), new ClosureBackedAction<Object>(action)
                ));
    }

    private <T> void registerCreator(Class<T> type, Closure<?> closure) {
        ModelRuleDescriptor descriptor = new SimpleModelRuleDescriptor("model." + modelPath);
        ModelSchema<T> schema = modelSchemaStore.getSchema(ModelType.of(type));
        if (!schema.getKind().isManaged()) {
            throw new InvalidModelRuleDeclarationException(descriptor, "Cannot create an element of type " + type.getName() + " as it is not a managed type");
        }

        modelRegistry.create(modelCreatorFactory.creator(descriptor, modelPath, schema, new ClosureBackedAction<T>(closure)));
    }

    public void configure(Closure<?> action) {
        executingDsl.set(true);
        try {
            new ClosureBackedAction<Object>(action).execute(this);
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
                getChildPath(name).registerCreator(clazz, closure);
                return null;
            } else if (args.length == 1 && args[0] instanceof Class) {
                Class<?> clazz = (Class<?>) args[0];
                Closure<?> closure = Closure.IDENTITY;
                getChildPath(name).registerCreator(clazz, closure);
                return null;
            } else {
                throw new MissingMethodException(name, getClass(), args);
            }
        }
    }

}