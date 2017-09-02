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

import com.google.common.io.ByteStreams;
import org.gradle.api.internal.tasks.compile.ApiClassExtractor;
import org.gradle.caching.internal.BuildCacheHasher;
import org.gradle.internal.IoActions;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hashing;
import org.gradle.util.DeprecationLogger;
import org.objectweb.asm.ClassReader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.zip.ZipEntry;

public class AbiExtractingClasspathResourceHasher implements ResourceHasher {
    private HashCode hashClassBytes(InputStream inputStream) throws IOException {
        // Use the ABI as the hash
        byte[] classBytes = ByteStreams.toByteArray(inputStream);
        ApiClassExtractor extractor = new ApiClassExtractor(Collections.<String>emptySet());
        ClassReader reader = new ClassReader(classBytes);
        if (extractor.shouldExtractApiClassFrom(reader)) {
            byte[] signature = extractor.extractApiClassFrom(reader);
            if (signature != null) {
                return Hashing.md5().hashBytes(signature);
            }
        }
        return null;
    }

    @Override
    public HashCode hash(RegularFileSnapshot fileSnapshot) {
        String name = fileSnapshot.getName();
        if (!isClassFile(name)) {
            return null;
        }
        InputStream inputStream = null;
        try {
            inputStream = Files.newInputStream(Paths.get(fileSnapshot.getPath()));
            return hashClassBytes(inputStream);
        } catch (Exception e) {
            DeprecationLogger.nagUserWith("Malformed class file [" + name + "] found on compile classpath, which means that this class will cause a compile error if referenced in a source file. Gradle 5.0 will no longer allow malformed classes on compile classpath.");
            return fileSnapshot.getContent().getContentMd5();
        } finally {
            IoActions.closeQuietly(inputStream);
        }
    }

    @Override
    public HashCode hash(ZipEntry zipEntry, InputStream zipInput) throws IOException {
        if (!isClassFile(zipEntry.getName())) {
            return null;
        }
        return hashClassBytes(zipInput);
    }

    private boolean isClassFile(String name) {
        return name.endsWith(".class");
    }

    @Override
    public void appendConfigurationToHasher(BuildCacheHasher hasher) {
        hasher.putString(getClass().getName());
    }
}
