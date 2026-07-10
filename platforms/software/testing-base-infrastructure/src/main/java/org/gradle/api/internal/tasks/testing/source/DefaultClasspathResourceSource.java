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

import org.gradle.api.tasks.testing.source.ClasspathResourceSource;
import org.gradle.api.tasks.testing.source.FilePosition;
import org.jspecify.annotations.Nullable;

public final class DefaultClasspathResourceSource implements ClasspathResourceSource {

    private final String classpathResourceName;
    private final FilePosition position;

    public DefaultClasspathResourceSource(String classpathResourceName, FilePosition position) {
        this.classpathResourceName = classpathResourceName;
        this.position = position;
    }

    @Override
    public String getClasspathResourceName() {
        return classpathResourceName;
    }

    @Nullable
    @Override
    public FilePosition getPosition() {
        return position;
    }
}
