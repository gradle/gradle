/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.changedetection.state;

import com.google.common.hash.Funnels;
import com.google.common.hash.Hasher;
import com.google.common.io.ByteStreams;
import org.gradle.api.UncheckedIOException;

import java.io.IOException;
import java.io.InputStream;

public class DefaultClasspathContentHasher implements ClasspathContentHasher {
    @Override
    public void appendContent(String name, InputStream inputStream, Hasher hasher) {
        // TODO: Deeper analysis of .class files for runtime
        // TODO: Sort entries in META-INF/ignore some entries
        // TODO: Sort entries in .properties/ignore some entries
        try {
            ByteStreams.copy(inputStream, Funnels.asOutputStream(hasher));
        } catch (IOException e) {
            throw new UncheckedIOException(String.format("Failed to hash file '%s' found on classpath", name), e);
        }
    }
}
