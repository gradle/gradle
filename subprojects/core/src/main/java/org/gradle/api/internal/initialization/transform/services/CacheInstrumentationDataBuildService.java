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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.initialization.transform.InstrumentationArtifactMetadata;
import org.gradle.api.internal.initialization.transform.utils.CachedInstrumentationAnalysisSerializer;
import org.gradle.api.internal.initialization.transform.utils.DefaultInstrumentationAnalysisSerializer;
import org.gradle.api.internal.initialization.transform.utils.InstrumentationAnalysisSerializer;
import org.gradle.api.internal.initialization.transform.utils.InstrumentationTransformUtils;
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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public abstract class CacheInstrumentationDataBuildService implements BuildService<BuildServiceParameters.None> {

    private final Map<Long, ResolutionData> resolutionData = new ConcurrentHashMap<>();
    private final Lazy<InjectedInstrumentationServices> internalServices = Lazy.locking().of(() -> getObjectFactory().newInstance(InjectedInstrumentationServices.class));
    private final Lazy<InstrumentationAnalysisSerializer> serializer = Lazy.locking().of(() -> new CachedInstrumentationAnalysisSerializer(new DefaultInstrumentationAnalysisSerializer(internalServices.get().getStringInterner())));
    private final Lazy<Cache<Set<File>, ExternalPluginsInstrumentationTypeRegistry>> typeRegistryCache = Lazy.locking().of(() -> CacheBuilder.newBuilder()
        .concurrencyLevel(1)
        .maximumSize(100)
        .build());

    @Inject
    protected abstract ObjectFactory getObjectFactory();

    public InstrumentationAnalysisSerializer getCachedInstrumentationAnalysisSerializer() {
        return serializer.get();
    }

    public InstrumentationTypeRegistry getInstrumentationTypeRegistry(long contextId) {
        InstrumentationTypeRegistry gradleCoreInstrumentationTypeRegistry = internalServices.get().getGradleCoreInstrumentationTypeRegistry();
        if (gradleCoreInstrumentationTypeRegistry.isEmpty()) {
            // In case core types registry is empty, it means we don't have any upgrades
            // in Gradle core, so we can return empty registry
            return InstrumentationTypeRegistry.empty();
        }
        return getResolutionData(contextId).getInstrumentationTypeRegistry();
    }

    /**
     * Returns the original file for the given metadata. It's possible that multiple files have the same content,
     * so this method returns just one. For instrumentation is not important which one is returned.
     */
    public File getOriginalFile(long contextId, InstrumentationArtifactMetadata metadata) {
        String hash = metadata.getArtifactHash();
        String artifactName = metadata.getArtifactName();
        return checkNotNull(getResolutionData(contextId).getOriginalFile(metadata.getArtifactHash()),
            "Original file for artifact with name '%s' and hash '%s' does not exist! " +
                "That indicates that artifact changed during resolution from another process. That is not supported!", artifactName, hash);
    }

    @Nullable
    public String getArtifactHash(long contextId, File file) {
        return getResolutionData(contextId).getArtifactHash(file);
    }

    public FileCollection getTypeHierarchyAnalysis(long contextId) {
        return getResolutionData(contextId).getTypeHierarchyAnalysisResult();
    }

    private ResolutionData getResolutionData(long contextId) {
        return checkNotNull(resolutionData.get(contextId), "Resolution data for id %s does not exist!", contextId);
    }

    public ResolutionScope newResolutionScope(long contextId) {
        ResolutionData resolutionData = this.resolutionData.compute(contextId, (__, value) -> {
            checkArgument(value == null, "Resolution data for id %s already exists! Was previous resolution scope closed properly?", contextId);
            return getObjectFactory().newInstance(ResolutionData.class, internalServices.get(), serializer.get(), typeRegistryCache.get());
        });
        return new ResolutionScope() {
            @Override
            public void setTypeHierarchyAnalysisResult(FileCollection analysisResult) {
                FileCollection typeHierarchyAnalysisResult = analysisResult.filter(InstrumentationTransformUtils::isTypeHierarchyAnalysisFile);
                resolutionData.getTypeHierarchyAnalysisResult().setFrom(typeHierarchyAnalysisResult);
                resolutionData.getTypeHierarchyAnalysisResult().finalizeValueOnRead();
            }

            @Override
            public void setOriginalClasspath(FileCollection originalClasspath) {
                resolutionData.getOriginalClasspath().setFrom(originalClasspath);
                resolutionData.getOriginalClasspath().finalizeValueOnRead();
            }

            @Override
            public void close() {
                CacheInstrumentationDataBuildService.this.resolutionData.remove(contextId);
            }
        };
    }

    public interface ResolutionScope extends AutoCloseable {

        void setTypeHierarchyAnalysisResult(FileCollection analysisResult);
        void setOriginalClasspath(FileCollection originalClasspath);

        @Override
        void close();
    }

    abstract static class ResolutionData {
        private final Lazy<InstrumentationTypeRegistry> instrumentationTypeRegistry;
        private final Lazy<Map<String, File>> hashToOriginalFile;
        private final Map<File, String> hashCache;
        private final InjectedInstrumentationServices internalServices;
        private final InstrumentationAnalysisSerializer serializer;
        private final Cache<Set<File>, ExternalPluginsInstrumentationTypeRegistry> typeRegistryCache;

        @Inject
        public ResolutionData(InjectedInstrumentationServices internalServices, InstrumentationAnalysisSerializer serializer, Cache<Set<File>, ExternalPluginsInstrumentationTypeRegistry> typeRegistryCache) {
            this.serializer = serializer;
            this.hashCache = new ConcurrentHashMap<>();
            this.typeRegistryCache = typeRegistryCache;
            this.hashToOriginalFile = Lazy.locking().of(() -> {
                Set<File> originalClasspath = getOriginalClasspath().getFiles();
                Map<String, File> originalFiles = Maps.newHashMapWithExpectedSize(originalClasspath.size());
                originalClasspath.forEach(file -> {
                    String fileHash = getArtifactHash(file);
                    if (fileHash != null) {
                        originalFiles.put(fileHash, file);
                    }
                });
                return originalFiles;
            });
            this.instrumentationTypeRegistry = Lazy.locking().of(() -> {
                Set<File> typeHierarchyAnalysis = getTypeHierarchyAnalysisResult().getFiles();
                return getInstrumentationTypeRegistryFromCache(typeHierarchyAnalysis);
            });
            this.internalServices = internalServices;
        }

        private ExternalPluginsInstrumentationTypeRegistry getInstrumentationTypeRegistryFromCache(Set<File> typeHierarchyAnalysis) {
            try {
                return typeRegistryCache.get(typeHierarchyAnalysis, () -> {
                    InstrumentationTypeRegistry gradleCoreInstrumentationTypeRegistry = internalServices.getGradleCoreInstrumentationTypeRegistry();
                    Map<String, Set<String>> directSuperTypes = mergeTypeHierarchyAnalysis(typeHierarchyAnalysis);
                    return new ExternalPluginsInstrumentationTypeRegistry(directSuperTypes, gradleCoreInstrumentationTypeRegistry);
                });
            } catch (ExecutionException e) {
                throw new RuntimeException(e);
            }
        }

        private Map<String, Set<String>> mergeTypeHierarchyAnalysis(Set<File> typeHierarchyAnalysis) {
            return typeHierarchyAnalysis.stream()
                .flatMap(file -> serializer.readTypeHierarchyAnalysis(file).entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, Sets::union));
        }

        abstract ConfigurableFileCollection getTypeHierarchyAnalysisResult();
        abstract ConfigurableFileCollection getOriginalClasspath();

        private InstrumentationTypeRegistry getInstrumentationTypeRegistry() {
            return instrumentationTypeRegistry.get();
        }

        @Nullable
        private File getOriginalFile(String hash) {
            return hashToOriginalFile.get().get(hash);
        }

        @Nullable
        private String getArtifactHash(File file) {
            return hashCache.computeIfAbsent(file, __ -> {
                Hasher hasher = Hashing.newHasher();
                FileSystemLocationSnapshot snapshot = internalServices.getFileSystemAccess().read(file.getAbsolutePath());
                if (snapshot.getType() == FileType.Missing) {
                    return null;
                }

                hasher.putHash(snapshot.getHash());
                hasher.putString(file.getName());
                return hasher.hash().toString();
            });
        }
    }
}
