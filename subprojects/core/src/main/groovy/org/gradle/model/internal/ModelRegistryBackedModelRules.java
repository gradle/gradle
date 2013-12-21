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

import com.google.common.collect.ImmutableList;
import org.gradle.api.Action;
import org.gradle.api.specs.Spec;
import org.gradle.internal.Factories;
import org.gradle.internal.Factory;
import org.gradle.model.ModelRule;
import org.gradle.model.ModelRules;
import org.gradle.model.internal.rules.ReflectiveRule;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Collections;

import static org.gradle.util.CollectionUtils.findFirst;

public class ModelRegistryBackedModelRules implements ModelRules {

    private final ModelRegistry modelRegistry;

    public ModelRegistryBackedModelRules(ModelRegistry modelRegistry) {
        this.modelRegistry = modelRegistry;
    }

    public <T> void register(String path, final T model) {
        @SuppressWarnings("unchecked") Class<T> aClass = (Class<T>) model.getClass();
        register(path, aClass, Factories.constant(model));
    }

    public <T> void register(String path, final Class<T> type, final Factory<? extends T> model) {
        modelRegistry.create(path, ImmutableList.<String>of(), new ModelCreator<T>() {
            public Class<T> getType() {
                return type;
            }

            public T create(Inputs inputs) {
                return model.create();
            }
        });
    }

    public void remove(String path) {
        modelRegistry.remove(path);
    }

    public void rule(ModelRule rule) {
        ReflectiveRule.rule(modelRegistry, rule);
    }

    public <T> void config(String path, Action<T> action) {
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

        final Class<?> modelType;

        if (actionType == null) {
            modelType = Object.class;
        } else {
            ParameterizedType actionParamaterizedType = (ParameterizedType) actionType;
            Type tType = actionParamaterizedType.getActualTypeArguments()[0];

            if (tType instanceof Class) {
                modelType = (Class) tType;
            } else if (tType instanceof ParameterizedType) {
                modelType = (Class) ((ParameterizedType) tType).getRawType();
            } else if (tType instanceof TypeVariable) {
                TypeVariable  typeVariable = (TypeVariable) tType;
                Type[] bounds = typeVariable.getBounds();
                return (Class<T>) bounds[0];
            } else {
                throw new RuntimeException("Don't know how to handle type: " + tType.getClass());
            }
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
