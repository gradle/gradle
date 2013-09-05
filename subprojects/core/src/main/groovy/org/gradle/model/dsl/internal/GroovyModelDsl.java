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
import groovy.lang.MissingMethodException;
import org.gradle.api.internal.ClosureBackedAction;
import org.gradle.model.ModelPath;
import org.gradle.model.ModelRules;
import org.gradle.model.dsl.ModelDsl;

public class GroovyModelDsl implements ModelDsl {

    private final ModelPath modelPath;
    private final ModelRules modelRules;

    public GroovyModelDsl(ModelRules modelRules) {
        this(null, modelRules);
    }

    private GroovyModelDsl(ModelPath modelPath, ModelRules modelRules) {
        this.modelPath = modelPath;
        this.modelRules = modelRules;
    }

    public ModelDsl get(String name) {
        return new GroovyModelDsl(modelPath == null ? ModelPath.path(name) : modelPath.child(name), modelRules);
    }

    public void configure(Closure<?> action) {
        modelRules.config(modelPath.toString(), new ClosureBackedAction<Object>(action));
    }

    public ModelDsl propertyMissing(String name) {
        return get(name);
    }

    public Void methodMissing(String name, Object argsObj) {
        Object[] args = (Object[]) argsObj;

        if (args.length != 1 || !(args[0] instanceof Closure)) {
            throw new MissingMethodException(name, getClass(), args);
        }

        Closure closure = (Closure) args[0];

        get(name).configure(closure);

        return null;
    }

}
