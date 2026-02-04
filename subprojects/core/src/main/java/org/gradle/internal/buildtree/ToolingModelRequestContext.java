/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.buildtree;

import java.util.Optional;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public class ToolingModelRequestContext {
    private final String modelName;
    @Nullable
    private final Object parameter;
    // this is true if the `fetch` API is used.
    private final boolean inResilientContext;

    public ToolingModelRequestContext(String modelName, @Nullable Object parameter, boolean inResilientContext) {
        this.modelName = modelName;
        this.parameter = parameter;
        this.inResilientContext = inResilientContext;
    }

    public String getModelName() {
        return modelName;
    }

    public Optional<Object> getParameter() {
        return Optional.ofNullable(parameter);
    }

    public boolean inResilientContext() {
        return inResilientContext;
    }
}
