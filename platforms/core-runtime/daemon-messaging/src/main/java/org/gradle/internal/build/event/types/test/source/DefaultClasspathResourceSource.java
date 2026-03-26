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

package org.gradle.internal.build.event.types.test.source;

import org.gradle.tooling.internal.protocol.events.InternalFilePosition;
import org.gradle.tooling.internal.protocol.test.source.InternalClasspathResourceSource;
import org.jspecify.annotations.Nullable;

import java.io.Serializable;

public class DefaultClasspathResourceSource implements InternalClasspathResourceSource, Serializable {

    private final String classpathResourceName;
    private final InternalFilePosition position;

    public DefaultClasspathResourceSource(String classpathResourceName, @Nullable InternalFilePosition position) {
        this.classpathResourceName = classpathResourceName;
        this.position = position;
    }

    @Override
    public String getClasspathResourceName() {
        return classpathResourceName;
    }

    @Override
    @Nullable
    public InternalFilePosition getPosition() {
        return position;
    }
}
