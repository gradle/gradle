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

package org.gradle.model.internal;

import org.gradle.api.Action;
import org.gradle.api.specs.Spec;
import org.gradle.model.ModelConfigurer;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collections;

import static org.gradle.util.CollectionUtils.findFirst;

public class ModelRegistryBackedModelConfigurer implements ModelConfigurer {

    private final ModelRegistry modelRegistry;

    public ModelRegistryBackedModelConfigurer(ModelRegistry modelRegistry) {
        this.modelRegistry = modelRegistry;
    }

    public <T> void configure(String path, Action<T> action) {
        final Class<T> modelType = getActionObjectType(action);
        modelRegistry.mutate(path, Collections.<String>emptyList(), ActionBackedModelMutator.<T>create(modelType, action));
    }

    private <T> Class<T> getActionObjectType(Action<T> action) {
        Class<? extends Action> aClass = action.getClass();
        Type[] genericInterfaces = aClass.getGenericInterfaces();
        Type actionType = findFirst(genericInterfaces, new Spec<Type>() {
            public boolean isSatisfiedBy(Type element) {
                return element instanceof ParameterizedType && ((ParameterizedType) element).getRawType().equals(Action.class);
            }
        });

        ParameterizedType actionParamaterizedType = (ParameterizedType) actionType;
        Type tType = actionParamaterizedType.getActualTypeArguments()[0];

        final Class<?> modelType;

        if (tType instanceof Class) {
            modelType = (Class) tType;
        } else if (tType instanceof ParameterizedType) {
            modelType = (Class) ((ParameterizedType) tType).getRawType();
        } else {
            throw new RuntimeException("!");
        }

        @SuppressWarnings("unchecked") Class<T> castModelType = (Class<T>) modelType;
        return castModelType;
    }

    private static class ActionBackedModelMutator<T> implements ModelMutator<T> {
        private final Class<T> modelType;
        private final Action<T> action;

        public static <T> ModelMutator<T> create(Class<T> type, Action<T> action) {
            return new ActionBackedModelMutator<T>(type, action);
        }

        public ActionBackedModelMutator(Class<T> modelType, Action<T> action) {
            this.modelType = modelType;
            this.action = action;
        }

        public Class<T> getType() {
            return modelType;
        }

        public void mutate(T object, Inputs inputs) {
            action.execute(object);
        }
    }
}
