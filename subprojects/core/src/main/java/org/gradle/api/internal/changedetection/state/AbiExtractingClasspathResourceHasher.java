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
import org.gradle.api.internal.changedetection.state.mirror.PhysicalFileSnapshot;
import org.gradle.api.internal.tasks.compile.ApiClassExtractor;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.caching.internal.BuildCacheHasher;
import org.gradle.internal.IoActions;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hashing;
import org.objectweb.asm.ClassReader;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.zip.ZipEntry;

@SuppressWarnings("Since15")
public class AbiExtractingClasspathResourceHasher implements ResourceHasher {
    private static final Logger LOGGER = Logging.getLogger(AbiExtractingClasspathResourceHasher.class);

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

    @Nullable
    @Override
    public HashCode hash(PhysicalFileSnapshot fileSnapshot, Iterable<String> relativePath) {
        String absolutePath = fileSnapshot.getAbsolutePath();
        Path path = Paths.get(absolutePath);
        if (!isClassFile(absolutePath)) {
            return null;
        }
        InputStream inputStream = null;
        try {
            inputStream = Files.newInputStream(path);
            return hashClassBytes(inputStream);
        } catch (Exception e) {
            LOGGER.debug("Malformed class file '{}' found on compile classpath. Falling back to full file hash instead of ABI hashing.", path.getFileName().toString(), e);
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
