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
import org.gradle.api.GradleException;
import org.gradle.api.internal.ClosureBackedAction;
import org.gradle.model.internal.core.Inputs;
import org.gradle.model.internal.core.ModelMutator;
import org.gradle.model.internal.core.ModelPath;
import org.gradle.model.internal.core.ModelReference;
import org.gradle.model.internal.core.rule.describe.ModelRuleDescriptor;
import org.gradle.model.internal.core.rule.describe.SimpleModelRuleDescriptor;
import org.gradle.model.internal.registry.ModelRegistry;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class NonTransformedModelDslBacking extends GroovyObjectSupport {

    // TODO include link to documentation giving more explanation of what's going on here.
    public static final String ATTEMPTED_INPUT_SYNTAX_USED_MESSAGE = "$() syntax cannot be used when model {} block is not a top level statement in the script";

    private final ModelPath modelPath;
    private final ModelRegistry modelRegistry;
    private AtomicBoolean executingDsl;

    public NonTransformedModelDslBacking(ModelRegistry modelRegistry) {
        this(new AtomicBoolean(), null, modelRegistry);
    }

    private NonTransformedModelDslBacking(AtomicBoolean executingDsl, ModelPath modelPath, ModelRegistry modelRegistry) {
        this.executingDsl = executingDsl;
        this.modelPath = modelPath;
        this.modelRegistry = modelRegistry;
    }

    private NonTransformedModelDslBacking getChildPath(String name) {
        ModelPath path = modelPath == null ? ModelPath.path(name) : modelPath.child(name);
        return new NonTransformedModelDslBacking(executingDsl, path, modelRegistry);
    }

    private void registerConfigurationAction(final Closure<?> action) {
        modelRegistry.mutate(new ModelMutator<Object>() {
            public ModelReference<Object> getSubject() {
                return ModelReference.untyped(modelPath);
            }

            public void mutate(Object object, Inputs inputs) {
                new ClosureBackedAction<Object>(action).execute(object);
            }

            public ModelRuleDescriptor getDescriptor() {
                return new SimpleModelRuleDescriptor("model." + modelPath);
            }

            public List<ModelReference<?>> getInputs() {
                return Collections.emptyList();
            }
        });
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
            if (args.length != 1 || !(args[0] instanceof Closure)) {
                throw new MissingMethodException(name, getClass(), args);
            } else {
                Closure closure = (Closure) args[0];
                getChildPath(name).registerConfigurationAction(closure);
                return null;
            }
        }
    }

}