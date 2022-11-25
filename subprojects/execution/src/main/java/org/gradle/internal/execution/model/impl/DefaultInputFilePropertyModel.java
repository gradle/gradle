/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.execution.model.impl;

import org.gradle.api.file.FileCollection;
import org.gradle.internal.execution.model.InputFilePropertyModel;
import org.gradle.internal.fingerprint.DirectorySensitivity;
import org.gradle.internal.fingerprint.FileNormalizer;
import org.gradle.internal.fingerprint.LineEndingSensitivity;
import org.gradle.internal.properties.InputBehavior;

import javax.annotation.Nullable;

public class DefaultInputFilePropertyModel extends AbstractFilePropertyModel implements InputFilePropertyModel {
    private final InputBehavior behavior;
    private final DirectorySensitivity directorySensitivity;
    private final LineEndingSensitivity lineEndingNormalization;

    public DefaultInputFilePropertyModel(String qualifiedName, @Nullable FileNormalizer normalizer, InputBehavior behavior, DirectorySensitivity directorySensitivity, LineEndingSensitivity lineEndingNormalization, @Nullable FileCollection value) {
        super(qualifiedName, value, normalizer);
        this.behavior = behavior;
        this.directorySensitivity = directorySensitivity;
        this.lineEndingNormalization = lineEndingNormalization;
    }

    @Override
    public InputBehavior getBehavior() {
        return behavior;
    }

    @Override
    public DirectorySensitivity getDirectorySensitivity() {
        return directorySensitivity;
    }

    @Override
    public LineEndingSensitivity getLineEndingNormalization() {
        return lineEndingNormalization;
    }
}
