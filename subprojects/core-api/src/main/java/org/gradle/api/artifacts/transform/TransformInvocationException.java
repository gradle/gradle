/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.artifacts.transform;

import org.gradle.api.GradleException;
import org.gradle.api.Incubating;
import org.gradle.internal.exceptions.Contextual;
import org.gradle.model.internal.type.ModelType;

import java.io.File;

/**
 * An exception to report a problem during transformation invocation.
 *
 * @since 5.0
 */
@Contextual
@Incubating
public class TransformInvocationException extends GradleException {

    private final File input;
    private final Class<? extends ArtifactTransform> transform;

    public TransformInvocationException(File input, Class<? extends ArtifactTransform> transform, Throwable cause) {
        super(format(input, transform), cause);
        this.input = input;
        this.transform = transform;
    }

    public File getInput() {
        return input;
    }

    public Class<? extends ArtifactTransform> getTransformImplementation() {
        return transform;
    }

    private static String format(File input, Class<? extends ArtifactTransform> transform) {
        return String.format("Failed to transform file '%s' using transform %s",
            input.getName(), ModelType.of(transform).getDisplayName());
    }
}
