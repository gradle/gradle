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

package org.gradle.api.internal.initialization.transform.services;

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

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.gradle.api.internal.initialization.transform.utils.InstrumentationTransformUtils.SUPER_TYPES_FILE_NAME;
import static org.gradle.api.internal.initialization.transform.utils.InstrumentationTransformUtils.readSuperTypes;

public abstract class CacheInstrumentationTypeRegistryBuildService implements BuildService<CacheInstrumentationTypeRegistryBuildService.Parameters> {

    /**
     * Can be removed once we actually have some upgrades, but without upgrades we currently can't test this
     */
    public static final String GENERATE_CLASS_HIERARCHY_WITHOUT_UPGRADES_PROPERTY = "org.gradle.internal.instrumentation.generateClassHierarchyWithoutUpgrades";

    public interface Parameters extends BuildServiceParameters {
        ConfigurableFileCollection getAnalyzeResult();
        ConfigurableFileCollection getOriginalClasspath();
    }

    @Inject
    protected abstract ObjectFactory getObjectFactory();

    private volatile InstrumentationTypeRegistry instrumentingTypeRegistry;
    private volatile Map<String, File> originalFiles;
    private volatile Map<File, String> hashCache;
    private final Lazy<InjectedInstrumentationServices> internalServices = Lazy.locking().of(() -> getObjectFactory().newInstance(InjectedInstrumentationServices.class));

    public InstrumentationTypeRegistry getInstrumentingTypeRegistry(GradleCoreInstrumentationTypeRegistry gradleCoreInstrumentationTypeRegistry) {
        if (!Boolean.getBoolean(GENERATE_CLASS_HIERARCHY_WITHOUT_UPGRADES_PROPERTY) && gradleCoreInstrumentationTypeRegistry.isEmpty()) {
            // In case core types registry is empty, it means we don't have any upgrades
            // in Gradle core, so we can return empty registry
            return InstrumentationTypeRegistry.empty();
        }

        if (instrumentingTypeRegistry == null) {
            synchronized (this) {
                if (instrumentingTypeRegistry == null) {
                    Map<String, Set<String>> directSuperTypes = readDirectSuperTypes();
                    instrumentingTypeRegistry = new ExternalPluginsInstrumentationTypeRegistry(directSuperTypes, gradleCoreInstrumentationTypeRegistry);
                }
            }
        }
        return instrumentingTypeRegistry;
    }

    private Map<String, Set<String>> readDirectSuperTypes() {
        Set<File> directories = getParameters().getAnalyzeResult().getFiles();
        return directories.stream()
            .map(dir -> new File(dir, SUPER_TYPES_FILE_NAME))
            .flatMap(file -> readSuperTypes(file).entrySet().stream())
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (first, second) -> first));
    }

    /**
     * Returns the original file for the given hash. It's possible that multiple files have the same content,
     * so this method returns just one. For instrumentation is not important which one is returned.
     */
    public File getOriginalFile(String hash) {
        if (originalFiles == null) {
            synchronized (this) {
                if (originalFiles == null) {
                    Map<String, File> originalFiles = new HashMap<>(getParameters().getOriginalClasspath().getFiles().size());
                    getParameters().getOriginalClasspath().forEach(file -> {
                        String fileHash = getArtifactHash(file);
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
    public String getArtifactHash(File file) {
        if (hashCache == null) {
            synchronized (this) {
                if (hashCache == null) {
                    this.hashCache = new ConcurrentHashMap<>();
                }
            }
        }
        return hashCache.computeIfAbsent(file, __ -> {
            Hasher hasher = Hashing.newHasher();
            InjectedInstrumentationServices services = internalServices.get();
            FileSystemLocationSnapshot snapshot = services.getFileSystemAccess().read(file.getAbsolutePath());
            if (snapshot.getType() == FileType.Missing) {
                return null;
            }

            hasher.putHash(snapshot.getHash());
            hasher.putString(file.getName());
            hasher.putBoolean(services.getGlobalCacheLocations().isInsideGlobalCache(file.getAbsolutePath()));
            return hasher.hash().toString();
        });
    }

    public void clear() {
        // Clear mutable data since service can be reused to resolve other configuration
        instrumentingTypeRegistry = null;
        originalFiles = null;
        hashCache = null;
    }
}
