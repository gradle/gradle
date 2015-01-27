/*
 * Copyright 2015 the original author or authors.
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
import com.google.common.collect.Lists;
import org.gradle.model.internal.type.ModelType;

import java.util.List;

public class DependencyOnlyExtractedModelRule implements ExtractedModelRule {

    private final List<ModelType<?>> dependencies;

    public DependencyOnlyExtractedModelRule(List<ModelType<?>> dependencyList) {
        this.dependencies = dependencyList;
    }

    @Override
    public void applyTo(ModelRegistrar registrar, ModelPath scope) {
    }

    public List<Class<?>> getRuleDependencies() {
        return Lists.transform(dependencies, new Function<ModelType<?>, Class<?>>() {
            @Override
            public Class<?> apply(ModelType<?> type) {
                return type.getRawClass();
            }
        });
    }
}
