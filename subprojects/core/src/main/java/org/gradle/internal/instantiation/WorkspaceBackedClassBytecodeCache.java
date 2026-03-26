/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.instantiation;

import org.gradle.internal.execution.workspace.ImmutableWorkspaceProvider;
import org.gradle.internal.instantiation.generator.GeneratedClassBytecodeCache;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Implementation of {@link GeneratedClassBytecodeCache} backed by
 * {@link CacheBasedImmutableWorkspaceProvider} for proper workspace management,
 * thread safety, and cache cleanup.
 */
public class WorkspaceBackedClassBytecodeCache implements GeneratedClassBytecodeCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(WorkspaceBackedClassBytecodeCache.class);
    private static final String BYTECODE_FILE = "bytecode.class";
    private static final String METADATA_FILE = "metadata.txt";

    private final ImmutableWorkspaceProvider workspaceProvider;

    public WorkspaceBackedClassBytecodeCache(ImmutableWorkspaceProvider workspaceProvider) {
        this.workspaceProvider = workspaceProvider;
    }

    @Override
    @Nullable
    public CachedClassData load(String cacheKey) {
        ImmutableWorkspaceProvider.ImmutableWorkspace workspace = workspaceProvider.getWorkspace(cacheKey);
        File location = workspace.getImmutableLocation();
        File bytecodeFile = new File(location, BYTECODE_FILE);
        File metadataFile = new File(location, METADATA_FILE);

        if (!bytecodeFile.exists() || !metadataFile.exists()) {
            return null;
        }

        try {
            byte[] bytecode = Files.readAllBytes(bytecodeFile.toPath());
            Metadata metadata = readMetadata(metadataFile);
            workspace.ensureUnSoftDeleted();
            return new CachedClassData(
                bytecode,
                metadata.generatedClassName,
                metadata.injectedServiceClassNames,
                metadata.annotationClassNames,
                metadata.managed,
                metadata.factoryId
            );
        } catch (IOException e) {
            LOGGER.debug("Failed to load cached bytecode for key {}", cacheKey, e);
            return null;
        }
    }

    @Override
    public void store(String cacheKey, CachedClassData data) {
        ImmutableWorkspaceProvider.ImmutableWorkspace workspace = workspaceProvider.getWorkspace(cacheKey);
        File location = workspace.getImmutableLocation();

        // Already cached
        if (new File(location, BYTECODE_FILE).exists()) {
            return;
        }

        workspace.withFileLock(() -> {
            // Double-check under lock
            File bytecodeFile = new File(location, BYTECODE_FILE);
            if (bytecodeFile.exists()) {
                return null;
            }

            try {
                Files.createDirectories(location.toPath());
                Files.write(bytecodeFile.toPath(), data.getBytecode());
                writeMetadata(new File(location, METADATA_FILE), data);
            } catch (IOException e) {
                LOGGER.debug("Failed to store cached bytecode for key {}", cacheKey, e);
            }
            return null;
        });
    }

    private static void writeMetadata(File file, CachedClassData data) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            writer.write(data.getGeneratedClassName());
            writer.newLine();
            writer.write(String.valueOf(data.isManaged()));
            writer.newLine();
            writer.write(String.valueOf(data.getFactoryId()));
            writer.newLine();
            writer.write(String.join(",", data.getInjectedServiceClassNames()));
            writer.newLine();
            writer.write(String.join(",", data.getAnnotationClassNames()));
            writer.newLine();
        }
    }

    private static Metadata readMetadata(File file) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            String generatedClassName = reader.readLine();
            boolean managed = Boolean.parseBoolean(reader.readLine());
            int factoryId = Integer.parseInt(reader.readLine());
            List<String> injectedServiceClassNames = parseCsvLine(reader.readLine());
            List<String> annotationClassNames = parseCsvLine(reader.readLine());
            return new Metadata(generatedClassName, managed, factoryId, injectedServiceClassNames, annotationClassNames);
        }
    }

    private static List<String> parseCsvLine(@Nullable String line) {
        if (line == null || line.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.asList(line.split(",")));
    }

    private static class Metadata {
        final String generatedClassName;
        final boolean managed;
        final int factoryId;
        final List<String> injectedServiceClassNames;
        final List<String> annotationClassNames;

        Metadata(String generatedClassName, boolean managed, int factoryId, List<String> injectedServiceClassNames, List<String> annotationClassNames) {
            this.generatedClassName = generatedClassName;
            this.managed = managed;
            this.factoryId = factoryId;
            this.injectedServiceClassNames = injectedServiceClassNames;
            this.annotationClassNames = annotationClassNames;
        }
    }
}
