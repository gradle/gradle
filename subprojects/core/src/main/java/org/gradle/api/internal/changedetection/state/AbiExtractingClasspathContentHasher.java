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
package org.gradle.api.internal.changedetection.state;

import com.google.common.hash.Hasher;
import com.google.common.io.ByteStreams;
import org.gradle.api.internal.tasks.compile.ApiClassExtractor;
import org.gradle.util.DeprecationLogger;
import org.gradle.util.internal.Java9ClassReader;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;

public class AbiExtractingClasspathContentHasher implements ClasspathContentHasher {
    private final ClasspathContentHasher fallback;

    public AbiExtractingClasspathContentHasher(ClasspathContentHasher fallback) {
        this.fallback = fallback;
    }

    private void hashClassBytes(InputStream inputStream, Hasher hasher) throws IOException {
        // Use the ABI as the hash
        byte[] classBytes = ByteStreams.toByteArray(inputStream);
        ApiClassExtractor extractor = new ApiClassExtractor(Collections.<String>emptySet());
        Java9ClassReader reader = new Java9ClassReader(classBytes);
        if (extractor.shouldExtractApiClassFrom(reader)) {
            byte[] signature = extractor.extractApiClassFrom(reader);
            if (signature != null) {
                hasher.putBytes(signature);
            }
        }
    }

    @Override
    public void appendContent(String name, InputStream inputStream, Hasher hasher) {
        // ignore non-class files
        if (name.endsWith(".class")) {
            try {
                hashClassBytes(inputStream, hasher);
            } catch (Exception e) {
                fallback.appendContent(name, inputStream, hasher);
                DeprecationLogger.nagUserWith("Malformed class file [" + name + "] found on compile classpath, which means that this class will cause a compile error if referenced in a source file. Gradle 5.0 will no longer allow malformed classes on compile classpath.");
            }
        }
    }
}
