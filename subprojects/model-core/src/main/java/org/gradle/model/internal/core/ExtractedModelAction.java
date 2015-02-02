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

import com.google.common.collect.ImmutableList;

import java.util.List;

public class ExtractedModelAction implements ExtractedModelRule {

    private final ModelActionRole role;
    private final ModelAction<?> action;
    private final List<? extends Class<?>> dependencies;

    public ExtractedModelAction(ModelActionRole role, ModelAction<?> action) {
        this(role, ImmutableList.<Class<?>>of(), action);
    }

    public ExtractedModelAction(ModelActionRole role, List<? extends Class<?>> dependencies, ModelAction<?> action) {
        this.role = role;
        this.action = action;
        this.dependencies = dependencies;
    }

    @Override
    public Type getType() {
        return Type.ACTION;
    }

    @Override
    public ModelCreator getCreator() {
        return null;
    }

    @Override
    public ModelActionRole getActionRole() {
        return role;
    }

    @Override
    public ModelAction<?> getAction() {
        return action;
    }

    @Override
    public List<? extends Class<?>> getRuleDependencies() {
        return dependencies;
    }
}
