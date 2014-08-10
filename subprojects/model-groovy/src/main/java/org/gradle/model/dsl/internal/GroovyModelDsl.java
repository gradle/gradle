/*
 * Copyright 2013 the original author or authors.
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
import org.gradle.api.internal.ClosureBackedAction;
import org.gradle.model.dsl.ModelDsl;
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

public class GroovyModelDsl extends GroovyObjectSupport implements ModelDsl {
    private final ModelPath modelPath;
    private final ModelRegistry modelRegistry;
    private AtomicBoolean executingDsl;

    public GroovyModelDsl(ModelRegistry modelRegistry) {
        this(new AtomicBoolean(), null, modelRegistry);
    }

    private GroovyModelDsl(AtomicBoolean executingDsl, ModelPath modelPath, ModelRegistry modelRegistry) {
        this.executingDsl = executingDsl;
        this.modelPath = modelPath;
        this.modelRegistry = modelRegistry;
    }

    private GroovyModelDsl getChildPath(String name) {
        ModelPath path = modelPath == null ? ModelPath.path(name) : modelPath.child(name);
        return new GroovyModelDsl(executingDsl, path, modelRegistry);
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

    public GroovyModelDsl propertyMissing(String name) {
        if (!executingDsl.get()) {
            throw new MissingPropertyException(name, getClass());
        }
        return getChildPath(name);
    }

    public Void methodMissing(String name, Object argsObj) {
        Object[] args = (Object[]) argsObj;

        if (!executingDsl.get() || args.length != 1 || !(args[0] instanceof Closure)) {
            throw new MissingMethodException(name, getClass(), args);
        }

        Closure closure = (Closure) args[0];

        getChildPath(name).registerConfigurationAction(closure);

        return null;
    }

}
