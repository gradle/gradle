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

package org.gradle.api.internal.tasks.testing.source;

import org.gradle.api.tasks.testing.source.FilePosition;
import org.gradle.api.tasks.testing.source.FileSource;
import org.jspecify.annotations.Nullable;

import java.io.File;

public final class DefaultFileSource implements FileSource {

    private final File file;
    private final FilePosition position;

    public DefaultFileSource(File file, @Nullable FilePosition position) {
        this.file = file;
        this.position = position;
    }

    @Override
    public File getFile() {
        return file;
    }

    @Override
    @Nullable
    public FilePosition getPosition() {
        return position;
    }
}
