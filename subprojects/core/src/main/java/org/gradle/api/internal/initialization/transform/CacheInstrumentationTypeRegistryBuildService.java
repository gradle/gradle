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

package org.gradle.api.internal.initialization.transform;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.internal.classpath.types.ExternalPluginsInstrumentationTypeRegistry;
import org.gradle.internal.classpath.types.GradleCoreInstrumentationTypeRegistry;
import org.gradle.internal.classpath.types.InstrumentationTypeRegistry;
import org.gradle.internal.file.FileType;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.hash.Hashing;
import org.gradle.internal.lazy.Lazy;
import org.gradle.internal.snapshot.FileSystemLocationSnapshot;
import org.gradle.internal.vfs.FileSystemAccess;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.gradle.api.internal.initialization.transform.CollectDirectClassSuperTypesTransform.FILE_HASH_PROPERTY_NAME;

public abstract class CacheInstrumentationTypeRegistryBuildService implements BuildService<CacheInstrumentationTypeRegistryBuildService.Parameters> {

    public interface Parameters extends BuildServiceParameters {
        ConfigurableFileCollection getClassHierarchy();
        ConfigurableFileCollection getOriginalClasspath();
    }

    @Inject
    protected abstract ObjectFactory getObjectFactory();

    private volatile InstrumentationTypeRegistry instrumentingTypeRegistry;
    private volatile Map<String, File> originalFiles;
    private final Lazy<FileSystemAccess> fileSystemAccess = Lazy.locking().of(() -> getObjectFactory().newInstance(InjectedInternalServices.class).getFileSystemAccess());

    public InstrumentationTypeRegistry getInstrumentingTypeRegistry(GradleCoreInstrumentationTypeRegistry gradleCoreInstrumentationTypeRegistry) {
        if (gradleCoreInstrumentationTypeRegistry.isEmpty()) {
            // In case core types registry is empty, it means we don't have any upgrades
            // in Gradle core, so we can return empty registry
            return InstrumentationTypeRegistry.empty();
        }

        if (instrumentingTypeRegistry == null) {
            synchronized (this) {
                if (instrumentingTypeRegistry == null) {
                    Map<String, Set<String>> classHierarchy = readClassHierarchy();
                    instrumentingTypeRegistry = new ExternalPluginsInstrumentationTypeRegistry(classHierarchy, gradleCoreInstrumentationTypeRegistry);
                }
            }
        }
        return instrumentingTypeRegistry;
    }

    private Map<String, Set<String>> readClassHierarchy() {
        Set<File> files = getParameters().getClassHierarchy().getFiles();
        Map<String, Set<String>> classHierarchy = new HashMap<>();
        files.forEach(file -> {
            Properties properties = new Properties();
            try (InputStream inputStream = Files.newInputStream(file.toPath())) {
                properties.load(inputStream);
                properties.entrySet().stream()
                    .filter(entry -> !entry.getKey().equals(FILE_HASH_PROPERTY_NAME))
                    .forEach(e -> {
                        Set<String> keyTypes = classHierarchy.computeIfAbsent((String) e.getKey(), __ -> new HashSet<>());
                        Collections.addAll(keyTypes, ((String) e.getValue()).split(","));
                    });
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        return classHierarchy;
    }

    public File getOriginalFile(String hash) {
        if (originalFiles == null) {
            synchronized (this) {
                if (originalFiles == null) {
                    Map<String, File> originalFiles = new HashMap<>(getParameters().getOriginalClasspath().getFiles().size());
                    getParameters().getOriginalClasspath().forEach(file -> {
                        String fileHash = hash(file);
                        if (fileHash != null) {
                            originalFiles.put(fileHash, file);
                        }
                    });
                    this.originalFiles = originalFiles;
                }
            }
        }
        return checkNotNull(originalFiles.get(hash));
    }

    @Nullable
    public String hash(File file) {
        Hasher hasher = Hashing.newHasher();
        FileSystemLocationSnapshot snapshot = fileSystemAccess.get().read(file.getAbsolutePath());
        if (snapshot.getType() == FileType.Missing) {
            return null;
        }

        hasher.putHash(fileSystemAccess.get().read(file.getAbsolutePath()).getHash());
        hasher.putString(file.getName());
        return hasher.hash().toString();
    }

    public void clear() {
        // Remove the reference to the registry, so that it can be garbage collected,
        // since build service instance is not deregistered
        instrumentingTypeRegistry = null;
        originalFiles = null;
    }

    interface InjectedInternalServices {
        @Inject
        FileSystemAccess getFileSystemAccess();
    }
}
