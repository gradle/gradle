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

package org.gradle.model.dsl.internal.spike;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import org.gradle.api.GradleException;
import org.gradle.api.specs.Spec;
import org.gradle.model.internal.core.ModelPath;
import org.gradle.util.CollectionUtils;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

class ModelRegistry {

    private Map<ModelPath, ModelCreator> creators = Maps.newHashMap();

    public void create(ModelPath path, ModelCreator creator) {
        creators.put(path, creator);
    }

    private Set<ModelPath> getPromisedPaths() {
        return ImmutableSet.<ModelPath>builder().addAll(creators.keySet()).build();
    }

    public Object get(ModelPath path) {
        ModelCreator modelCreator = creators.get(path);
        Map<String, Object> inputs = Maps.toMap(modelCreator.getInputPaths(), new Function<String, Object>() {
            public Object apply(String inputPath) {
                return get(ModelPath.path(inputPath));
            }
        });
        Object result = modelCreator.create(inputs);

        Set<ModelPath> promisedPaths = getPromisedPaths();
        for (ModelPath modelPath : promisedPaths) {
            if (path.isDirectChild(modelPath)) {
                closeChild(result, modelPath);
            }
        }

        return result;
    }

    private void closeChild(Object parent, ModelPath childPath) {
        try {
            String fieldName = childPath.getName();
            final String setterName = String.format("set%s%s", fieldName.substring(0, 1).toUpperCase(), fieldName.substring(1));
            Method setter = CollectionUtils.findFirst(parent.getClass().getDeclaredMethods(), new Spec<Method>() {
                public boolean isSatisfiedBy(Method method) {
                    return method.getName().equals(setterName);
                }
            });
            setter.invoke(parent, get(childPath));
        } catch (Exception e) {
            throw new GradleException("Could not close model element children", e);
        }
    }
}
