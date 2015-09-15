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

package org.gradle.model.internal.type;

import org.gradle.model.ModelMap;
import org.gradle.model.ModelSet;
import org.gradle.model.collection.ManagedSet;

import java.util.List;
import java.util.Set;

public abstract class ModelTypes {

    public static <I> ModelType<ModelMap<I>> modelMap(Class<I> type) {
        return modelMap(ModelType.of(type));
    }

    public static <I> ModelType<ModelMap<I>> modelMap(ModelType<I> type) {
        return new ModelType.Builder<ModelMap<I>>() {
        }.where(
            new ModelType.Parameter<I>() {
            }, type
        ).build();
    }

    public static <I> ModelType<ModelSet<I>> modelSet(ModelType<I> type) {
        return new ModelType.Builder<ModelSet<I>>() {
        }.where(
            new ModelType.Parameter<I>() {
            }, type
        ).build();
    }

    public static <I> ModelType<ManagedSet<I>> managedSet(ModelType<I> type) {
        return new ModelType.Builder<ManagedSet<I>>() {
        }.where(
            new ModelType.Parameter<I>() {
            }, type
        ).build();
    }

    public static <I> ModelType<List<I>> list(ModelType<I> type) {
        return new ModelType.Builder<List<I>>() {
        }.where(
            new ModelType.Parameter<I>() {
            }, type
        ).build();
    }

    public static <I> ModelType<Set<I>> set(ModelType<I> type) {
        return new ModelType.Builder<Set<I>>() {
        }.where(
            new ModelType.Parameter<I>() {
            }, type
        ).build();
    }
}
