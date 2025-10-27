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

package org.gradle.tooling.provider.model.internal;

import org.gradle.internal.problems.failure.Failure;
import org.gradle.tooling.provider.model.ToolingModelBuilder;
import org.jspecify.annotations.NullMarked;

import java.util.Collections;
import java.util.List;

/**
 * Internal type for a model builder result that contains additional data beyond the model itself if needed.
 * <p>
 * {@link ToolingModelBuilder} can optionally return a {@link ToolingModelBuilderResultInternal} instead of a raw model.
 * {@link ToolingModelBuilderResultInternal} is then unpacked before the model is returned to the client.
 */
@NullMarked
public class ToolingModelBuilderResultInternal {

    private final Object model;
    private final List<Failure> failures;

    private ToolingModelBuilderResultInternal(Object model, List<Failure> failures) {
        this.model = model;
        this.failures = failures;
    }

    public Object getModel() {
        return model;
    }

    public List<Failure> getFailures() {
        return failures;
    }

    public static ToolingModelBuilderResultInternal of(Object model) {
        return new ToolingModelBuilderResultInternal(model, Collections.emptyList());
    }

    public static ToolingModelBuilderResultInternal of(Object model, List<Failure> failures) {
        return new ToolingModelBuilderResultInternal(model, failures);
    }
}
