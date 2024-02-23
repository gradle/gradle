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
import org.gradle.api.file.FileCollection;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.internal.classpath.types.ExternalPluginsInstrumentationTypeRegistry;
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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.gradle.api.internal.initialization.transform.utils.InstrumentationTransformUtils.SUPER_TYPES_FILE_NAME;
import static org.gradle.api.internal.initialization.transform.utils.InstrumentationTransformUtils.readSuperTypes;

public abstract class CacheInstrumentationDataBuildService implements BuildService<BuildServiceParameters.None> {

    /**
     * Can be removed once we actually have some upgrades, but without upgrades we currently can't test this
     */
    public static final String GENERATE_CLASS_HIERARCHY_WITHOUT_UPGRADES_PROPERTY = "org.gradle.internal.instrumentation.generateClassHierarchyWithoutUpgrades";

    @Inject
    protected abstract ObjectFactory getObjectFactory();

    private final Map<Long, ResolutionData> resolutionScopes = new ConcurrentHashMap<>();
    private final Lazy<InjectedInstrumentationServices> internalServices = Lazy.locking().of(() -> getObjectFactory().newInstance(InjectedInstrumentationServices.class));

    public InstrumentationTypeRegistry getInstrumentingTypeRegistry(long contextId) {
        InstrumentationTypeRegistry gradleCoreInstrumentationTypeRegistry = internalServices.get().getGradleCoreInstrumentingTypeRegistry();
        if (!Boolean.getBoolean(GENERATE_CLASS_HIERARCHY_WITHOUT_UPGRADES_PROPERTY) && gradleCoreInstrumentationTypeRegistry.isEmpty()) {
            // In case core types registry is empty, it means we don't have any upgrades
            // in Gradle core, so we can return empty registry
            return InstrumentationTypeRegistry.empty();
        }
        return getResolutionData(contextId).getInstrumentingTypeRegistry();
    }

    /**
     * Returns the original file for the given hash. It's possible that multiple files have the same content,
     * so this method returns just one. For instrumentation is not important which one is returned.
     */
    public File getOriginalFile(long contextId, String hash) {
        return checkNotNull(getResolutionData(contextId).getOriginalFile(hash));
    }

    @Nullable
    public String getArtifactHash(long contextId, File file) {
        return getResolutionData(contextId).getArtifactHash(file);
    }

    public FileCollection getOriginalClasspath(long contextId) {
        return getResolutionData(contextId).getOriginalClasspath();
    }

    private ResolutionData getResolutionData(long contextId) {
        return checkNotNull(resolutionScopes.get(contextId), "Resolution data for id %s does not exist!", contextId);
    }

    public ResolutionScope newResolutionScope(long contextId) {
        ResolutionData resolutionData = resolutionScopes.compute(contextId, (__, value) -> {
            checkArgument(value == null, "Resolution data for id %s already exists! Was previous resolution scope closed properly?", contextId);
            return getObjectFactory().newInstance(ResolutionData.class, internalServices.get());
        });
        return new ResolutionScope() {
            @Override
            public void setAnalysisResult(FileCollection analysisResult) {
                resolutionData.getAnalysisResult().setFrom(analysisResult);
            }

            @Override
            public void setOriginalClasspath(FileCollection originalClasspath) {
                resolutionData.getOriginalClasspath().setFrom(originalClasspath);
            }

            @Override
            public void close() {
                resolutionScopes.remove(contextId);
            }
        };
    }

    public interface ResolutionScope extends AutoCloseable {

        void setAnalysisResult(FileCollection analysisResult);
        void setOriginalClasspath(FileCollection originalClasspath);

        @Override
        void close();
    }

    abstract static class ResolutionData {
        private final Lazy<InstrumentationTypeRegistry> instrumentingTypeRegistry;
        private final Lazy<Map<String, File>> hashToOriginalFile;
        private final Map<File, String> hashCache;
        private final InjectedInstrumentationServices internalServices;

        @Inject
        public ResolutionData(InjectedInstrumentationServices internalServices) {
            this.hashCache = new ConcurrentHashMap<>();
            this.hashToOriginalFile = Lazy.locking().of(() -> {
                Map<String, File> originalFiles = new HashMap<>(getOriginalClasspath().getFiles().size());
                getOriginalClasspath().forEach(file -> {
                    String fileHash = getArtifactHash(file);
                    if (fileHash != null) {
                        originalFiles.put(fileHash, file);
                    }
                });
                return originalFiles;
            });
            this.instrumentingTypeRegistry = Lazy.locking().of(() -> {
                InstrumentationTypeRegistry gradleCoreInstrumentingTypeRegistry = internalServices.getGradleCoreInstrumentingTypeRegistry();
                Map<String, Set<String>> directSuperTypes = readDirectSuperTypes();
                return new ExternalPluginsInstrumentationTypeRegistry(directSuperTypes, gradleCoreInstrumentingTypeRegistry);
            });
            this.internalServices = internalServices;
        }

        public abstract ConfigurableFileCollection getAnalysisResult();
        public abstract ConfigurableFileCollection getOriginalClasspath();

        private Map<String, Set<String>> readDirectSuperTypes() {
            Set<File> directories = getAnalysisResult().getFiles();
            return directories.stream()
                .filter(File::isDirectory)
                .map(dir -> new File(dir, SUPER_TYPES_FILE_NAME))
                .flatMap(file -> readSuperTypes(file).entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (first, second) -> first));
        }

        public InstrumentationTypeRegistry getInstrumentingTypeRegistry() {
            return instrumentingTypeRegistry.get();
        }

        @Nullable
        public File getOriginalFile(String hash) {
            return hashToOriginalFile.get().get(hash);
        }

        @Nullable
        public String getArtifactHash(File file) {
            return hashCache.computeIfAbsent(file, __ -> {
                Hasher hasher = Hashing.newHasher();
                FileSystemLocationSnapshot snapshot = internalServices.getFileSystemAccess().read(file.getAbsolutePath());
                if (snapshot.getType() == FileType.Missing) {
                    return null;
                }

                hasher.putHash(snapshot.getHash());
                hasher.putString(file.getName());
                hasher.putBoolean(internalServices.getGlobalCacheLocations().isInsideGlobalCache(file.getAbsolutePath()));
                return hasher.hash().toString();
            });
        }

    }
}
