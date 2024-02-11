/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.tasks;

import org.gradle.api.NonNullApi;
import org.gradle.api.tasks.ClasspathNormalizer;
import org.gradle.api.tasks.CompileClasspathNormalizer;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskInputFilePropertyBuilder;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.execution.model.InputNormalizer;
import org.gradle.internal.fingerprint.DirectorySensitivity;
import org.gradle.internal.fingerprint.FileNormalizer;
import org.gradle.internal.fingerprint.LineEndingSensitivity;
import org.gradle.internal.properties.InputFilePropertyType;
import org.gradle.internal.properties.StaticValue;

@NonNullApi
public class DefaultTaskInputFilePropertyRegistration extends AbstractTaskFilePropertyRegistration implements TaskInputFilePropertyRegistration {

    private final InputFilePropertyType filePropertyType;
    private boolean skipWhenEmpty;
    private DirectorySensitivity directorySensitivity = DirectorySensitivity.DEFAULT;
    private LineEndingSensitivity lineEndingSensitivity = LineEndingSensitivity.DEFAULT;
    private FileNormalizer normalizer = InputNormalizer.ABSOLUTE_PATH;

    public DefaultTaskInputFilePropertyRegistration(StaticValue value, InputFilePropertyType filePropertyType) {
        super(value);
        this.filePropertyType = filePropertyType;
    }

    @Override
    public InputFilePropertyType getFilePropertyType() {
        return filePropertyType;
    }

    @Override
    public TaskInputFilePropertyBuilderInternal withPropertyName(String propertyName) {
        setPropertyName(propertyName);
        return this;
    }

    @Override
    public boolean isSkipWhenEmpty() {
        return skipWhenEmpty;
    }

    @Override
    public TaskInputFilePropertyBuilderInternal skipWhenEmpty(boolean skipWhenEmpty) {
        this.skipWhenEmpty = skipWhenEmpty;
        return this;
    }

    @Override
    public TaskInputFilePropertyBuilderInternal skipWhenEmpty() {
        return skipWhenEmpty(true);
    }

    @Override
    public TaskInputFilePropertyBuilderInternal optional(boolean optional) {
        setOptional(optional);
        return this;
    }

    @Override
    public TaskInputFilePropertyBuilderInternal optional() {
        return optional(true);
    }

    @Override
    public TaskInputFilePropertyBuilderInternal withPathSensitivity(PathSensitivity sensitivity) {
        return withInternalNormalizer(InputNormalizer.determineNormalizerForPathSensitivity(sensitivity));
    }

    @Override
    public TaskInputFilePropertyBuilderInternal withNormalizer(Class<? extends org.gradle.api.tasks.FileNormalizer> normalizer) {
        if (normalizer == ClasspathNormalizer.class) {
            return withInternalNormalizer(InputNormalizer.RUNTIME_CLASSPATH);
        } else if (normalizer == CompileClasspathNormalizer.class) {
            return withInternalNormalizer(InputNormalizer.COMPILE_CLASSPATH);
        } else {
            DeprecationLogger.deprecateBehaviour("Setting an input property's normalizer to a custom implementation of FileNormalizer")
                .willBeRemovedInGradle9()
                // TODO Document this
                .undocumented();
            return this;
        }
    }

    @Override
    public TaskInputFilePropertyBuilderInternal withInternalNormalizer(FileNormalizer normalizer) {
        this.normalizer = normalizer;
        return this;
    }

    @Override
    public FileNormalizer getNormalizer() {
        return normalizer;
    }

    @Override
    public DirectorySensitivity getDirectorySensitivity() {
        return directorySensitivity;
    }

    @Override
    public TaskInputFilePropertyBuilder ignoreEmptyDirectories() {
        this.directorySensitivity = DirectorySensitivity.IGNORE_DIRECTORIES;
        return this;
    }

    @Override
    public TaskInputFilePropertyBuilder ignoreEmptyDirectories(boolean ignoreDirectories) {
        this.directorySensitivity = ignoreDirectories ? DirectorySensitivity.IGNORE_DIRECTORIES : DirectorySensitivity.DEFAULT;
        return this;
    }

    @Override
    public LineEndingSensitivity getLineEndingNormalization() {
        return lineEndingSensitivity;
    }

    @Override
    public TaskInputFilePropertyBuilder normalizeLineEndings() {
        this.lineEndingSensitivity = LineEndingSensitivity.NORMALIZE_LINE_ENDINGS;
        return this;
    }

    @Override
    public TaskInputFilePropertyBuilder normalizeLineEndings(boolean normalizeLineEndings) {
        this.lineEndingSensitivity = normalizeLineEndings ? LineEndingSensitivity.NORMALIZE_LINE_ENDINGS : LineEndingSensitivity.DEFAULT;
        return this;
    }

    @Override
    public String toString() {
        return getPropertyName() + " (" + getNormalizer() + ")";
    }
}
