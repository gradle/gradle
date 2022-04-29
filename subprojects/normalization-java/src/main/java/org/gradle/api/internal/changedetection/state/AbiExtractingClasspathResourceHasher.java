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

import org.gradle.api.internal.file.archive.ZipEntry;
import org.gradle.internal.fingerprint.hashing.RegularFileSnapshotContext;
import org.gradle.internal.fingerprint.hashing.ResourceHasher;
import org.gradle.internal.fingerprint.hashing.ZipEntryContext;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.io.IoFunction;
import org.gradle.internal.normalization.java.ApiClassExtractor;
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
    public static final AbiExtractingClasspathResourceHasher DEFAULT = withFallback(new ApiClassExtractor(Collections.emptySet()));

    private final ApiClassExtractor extractor;
    private final FallbackStrategy fallbackStrategy;

    private AbiExtractingClasspathResourceHasher(ApiClassExtractor extractor, FallbackStrategy fallbackStrategy) {
        this.extractor = extractor;
        this.fallbackStrategy = fallbackStrategy;
    }

    public static AbiExtractingClasspathResourceHasher withFallback(ApiClassExtractor extractor) {
        return new AbiExtractingClasspathResourceHasher(extractor, FallbackStrategy.FULL_HASH);
    }

    public static AbiExtractingClasspathResourceHasher withoutFallback(ApiClassExtractor extractor) {
        return new AbiExtractingClasspathResourceHasher(extractor, FallbackStrategy.NONE);
    }

    @Nullable
    private HashCode hashClassBytes(byte[] classBytes) {
        // Use the ABI as the hash
        ClassReader reader = new ClassReader(classBytes);
        return extractor.extractApiClassFrom(reader)
            .map(Hashing::hashBytes)
            .orElse(null);
    }

    @Nullable
    @Override
    public HashCode hash(RegularFileSnapshotContext fileSnapshotContext) throws IOException {
        RegularFileSnapshot fileSnapshot = fileSnapshotContext.getSnapshot();

        if (isNotClassFile(fileSnapshot.getName())) {
            return null;
        }

        return fallbackStrategy.handle(fileSnapshot, snapshot -> {
            Path path = Paths.get(snapshot.getAbsolutePath());
            byte[] classBytes = Files.readAllBytes(path);
            return hashClassBytes(classBytes);
        });
    }

    @Override
    public HashCode hash(ZipEntryContext zipEntryContext) throws IOException {
        ZipEntry zipEntry = zipEntryContext.getEntry();

        if (isNotClassFile(zipEntry.getName())) {
            return null;
        }

        // A failure to read the zip entry content is a failure to read from the zip stream and should
        // be handled as a failure at the file level rather than at the entry level
        byte[] content = zipEntry.getContent();

        // If there is a problem with hashing the public api of the zip entry, use a fallback strategy (if available) to
        // calculate a fallback hash for the entry
        return fallbackStrategy.handle(new ZipEntryContent(zipEntry.getName(), content), entry -> hashClassBytes(content));
    }

    private boolean isNotClassFile(String name) {
        return !name.endsWith(".class");
    }

    @Override
    public void appendConfigurationToHasher(Hasher hasher) {
        hasher.putString(getClass().getName());
        extractor.appendConfigurationToHasher(hasher);
    }

    private static class ZipEntryContent {
        final String name;
        final byte[] content;

        ZipEntryContent(String name, byte[] content) {
            this.name = name;
            this.content = content;
        }
    }

    enum FallbackStrategy {
        FULL_HASH() {
            @Nullable
            @Override
            HashCode handle(RegularFileSnapshot fileSnapshot, IoFunction<RegularFileSnapshot, HashCode> function) {
                try {
                    return function.apply(fileSnapshot);
                } catch (Exception e) {
                    LOGGER.debug("Malformed class file '{}' found on compile classpath. Falling back to full file hash instead of ABI hashing.", fileSnapshot.getName(), e);
                    return fileSnapshot.getHash();
                }
            }

            @Nullable
            @Override
            HashCode handle(ZipEntryContent zipEntry, IoFunction<ZipEntryContent, HashCode> function) {
                try {
                    return function.apply(zipEntry);
                } catch(Exception e) {
                    LOGGER.debug("Malformed class file '{}' found on compile classpath. Falling back to full file hash instead of ABI hashing.", zipEntry.name, e);
                    return Hashing.hashBytes(zipEntry.content);
                }
            }
        },
        NONE() {
            @Nullable
            @Override
            HashCode handle(RegularFileSnapshot fileSnapshot, IoFunction<RegularFileSnapshot, HashCode> function) throws IOException {
                return function.apply(fileSnapshot);
            }

            @Nullable
            @Override
            HashCode handle(ZipEntryContent zipEntry, IoFunction<ZipEntryContent, HashCode> function) throws IOException {
                return function.apply(zipEntry);
            }
        };

        @Nullable
        abstract  HashCode handle(RegularFileSnapshot fileSnapshot, IoFunction<RegularFileSnapshot, HashCode> function) throws IOException;

        @Nullable
        abstract HashCode handle(ZipEntryContent zipEntry, IoFunction<ZipEntryContent, HashCode> function) throws IOException;
    }
}
