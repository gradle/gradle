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

package org.gradle.model.internal.core;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.gradle.model.internal.type.ModelType;

import java.util.List;

public class ExtractedModelMutator implements ExtractedModelRule {

    private final ModelActionRole role;
    private final ModelAction<?> action;
    private final List<ModelType<?>> dependencies;

    public ExtractedModelMutator(ModelActionRole role, ModelAction<?> action) {
        this(role, action, ImmutableList.<ModelType<?>>of());
    }

    public ExtractedModelMutator(ModelActionRole role, ModelAction<?> action, List<ModelType<?>> dependencies) {
        this.role = role;
        this.action = action;
        this.dependencies = dependencies;
    }

    @Override
    public void applyTo(ModelRegistrar registrar, ModelPath scope) {
        registrar.apply(role, action, scope);
    }

    @Override
    public List<Class<?>> getRuleDependencies() {
        return Lists.transform(dependencies, new Function<ModelType<?>, Class<?>>() {
            @Override
            public Class<?> apply(ModelType<?> type) {
                return type.getRawClass();
            }
        });
    }
}
