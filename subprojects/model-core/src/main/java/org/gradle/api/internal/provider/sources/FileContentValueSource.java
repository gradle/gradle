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

package org.gradle.api.internal.provider.sources;

import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;
import org.gradle.api.tasks.InputFile;

import javax.annotation.Nullable;
import java.io.File;

public abstract class FileContentValueSource<T> implements ValueSource<T, FileContentValueSource.Parameters> {

    public interface Parameters extends ValueSourceParameters {

        @InputFile
        RegularFileProperty getFile();
    }

    @Nullable
    @Override
    public T obtain() {
        @Nullable final RegularFile regularFile = getParameters().getFile().getOrNull();
        if (regularFile == null) {
            return null;
        }
        final File file = regularFile.getAsFile();
        if (!file.isFile()) {
            return null;
        }
        return obtainFrom(file);
    }

    protected abstract T obtainFrom(File file);
}
