/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.tasks.properties;

import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.tasks.FileNormalizer;
import org.gradle.internal.execution.UnitOfWork.InputPropertyType;
import org.gradle.internal.fingerprint.DirectorySensitivity;
import org.gradle.internal.fingerprint.LineEndingSensitivity;

import javax.annotation.Nullable;

public class DefaultInputFilePropertySpec extends AbstractFilePropertySpec implements InputFilePropertySpec {
    private final InputPropertyType type;
    private final DirectorySensitivity directorySensitivity;
    private final LineEndingSensitivity lineEndingSensitivity;
    private final PropertyValue value;

    public DefaultInputFilePropertySpec(
        String propertyName,
        Class<? extends FileNormalizer> normalizer,
        FileCollectionInternal files,
        PropertyValue value,
        InputPropertyType type,
        DirectorySensitivity directorySensitivity,
        LineEndingSensitivity lineEndingSensitivity
    ) {
        super(propertyName, normalizer, files);
        this.type = type;
        this.directorySensitivity = directorySensitivity;
        this.lineEndingSensitivity = lineEndingSensitivity;
        this.value = value;
    }

    @Override
    public InputPropertyType getType() {
        return type;
    }

    @Override
    public DirectorySensitivity getDirectorySensitivity() {
        return directorySensitivity;
    }

    @Override
    public LineEndingSensitivity getLineEndingNormalization() {
        return lineEndingSensitivity;
    }

    @Override
    @Nullable
    public Object getValue() {
        return value.call();
    }
}
