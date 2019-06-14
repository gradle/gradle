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
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.snapshot.RegularFileSnapshot;
import org.objectweb.asm.ClassReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

public class AbiExtractingClasspathResourceHasher implements ResourceHasher {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbiExtractingClasspathResourceHasher.class);

    private HashCode hashClassBytes(byte[] classBytes) {
        // Use the ABI as the hash
        ApiClassExtractor extractor = new ApiClassExtractor(Collections.emptySet());
        ClassReader reader = new ClassReader(classBytes);
        if (extractor.shouldExtractApiClassFrom(reader)) {
            byte[] signature = extractor.extractApiClassFrom(reader);
            if (signature != null) {
                return Hashing.hashBytes(signature);
            }
        }
        return null;
    }

    @Nullable
    @Override
    public HashCode hash(RegularFileSnapshot fileSnapshot) {
        try {
            if (!isClassFile(fileSnapshot.getName())) {
                return null;
            }

            Path path = Paths.get(fileSnapshot.getAbsolutePath());
            byte[] classBytes = Files.readAllBytes(path);
            return hashClassBytes(classBytes);
        } catch (Exception e) {
            LOGGER.debug("Malformed class file '{}' found on compile classpath. Falling back to full file hash instead of ABI hashing.", fileSnapshot.getName(), e);
            return fileSnapshot.getHash();
        }
    }

    @Override
    public HashCode hash(ZipEntry zipEntry) throws IOException {
        if (!isClassFile(zipEntry.getName())) {
            return null;
        }
        byte[] content = new byte[zipEntry.size()];
        ByteStreams.readFully(zipEntry.getInputStream(), content);
        return hashClassBytes(content);
    }

    private boolean isClassFile(String name) {
        return name.endsWith(".class");
    }

    @Override
    public void appendConfigurationToHasher(Hasher hasher) {
        hasher.putString(getClass().getName());
    }
}
