/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ArtifactView;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.DependencyVerifyingModuleComponentRepository;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepository;
import org.gradle.api.internal.artifacts.verification.DependencyVerifierBuilder;
import org.gradle.api.internal.artifacts.verification.model.ChecksumKind;
import org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationsXmlReader;
import org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationsXmlWriter;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.invocation.Gradle;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;
import org.gradle.internal.deprecation.DeprecatableConfiguration;
import org.gradle.internal.hash.HashUtil;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;

public class WriteDependencyVerificationFile implements DependencyVerificationOverride, ArtifactVerificationOperation {
    private static final Logger LOGGER = LoggerFactory.getLogger(WriteDependencyVerificationFile.class);
    private static final Action<ArtifactView.ViewConfiguration> MODULE_COMPONENT_FILES = conf -> {
        conf.componentFilter(id -> id instanceof ModuleComponentIdentifier);
        conf.setLenient(true);
    };

    private final DependencyVerifierBuilder verificationsBuilder = new DependencyVerifierBuilder();
    private final File buildDirectory;
    private final BuildOperationExecutor buildOperationExecutor;
    private final Map<FileChecksum, String> cachedChecksums = Maps.newConcurrentMap();
    private final Set<ChecksumEntry> entriesToBeWritten = Sets.newLinkedHashSetWithExpectedSize(512);

    public WriteDependencyVerificationFile(File buildDirectory, BuildOperationExecutor buildOperationExecutor) {
        this.buildDirectory = buildDirectory;
        this.buildOperationExecutor = buildOperationExecutor;
    }

    @Override
    public ModuleComponentRepository overrideDependencyVerification(ModuleComponentRepository original) {
        return new DependencyVerifyingModuleComponentRepository(original, this);
    }

    @Override
    public void buildFinished(Gradle gradle) {
        File verifFile = DependencyVerificationOverride.dependencyVerificationsFile(buildDirectory);
        try {
            maybeReadExistingFile(verifFile);
            computeHashsConcurrently(gradle);
            writeEntriesSerially();
            serializeResult(verifFile);
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private void serializeResult(File verifFile) throws IOException {
        DependencyVerificationsXmlWriter.serialize(
            verificationsBuilder.build(),
            new FileOutputStream(verifFile)
        );
    }

    private void maybeReadExistingFile(File verifFile) throws FileNotFoundException {
        if (verifFile.exists()) {
            LOGGER.info("Found dependency verification metadata file, updating");
            DependencyVerificationsXmlReader.readFromXml(new FileInputStream(verifFile), verificationsBuilder);
        }
    }

    private void writeEntriesSerially() {
        entriesToBeWritten.stream()
            .sorted()
            .forEachOrdered(entry -> {
                verificationsBuilder.addChecksum(entry.id, entry.checksumKind, entry.checksum);
            });
    }

    private void computeHashsConcurrently(Gradle gradle) {
        buildOperationExecutor.runAll(queue -> {
            Set<Project> allprojects = gradle.getRootProject().getAllprojects();
            for (Project project : allprojects) {
                queue.add(new RunnableBuildOperation() {
                    @Override
                    public void run(BuildOperationContext context) {
                        resolveAllConfigurationsAndForceDownload(project);
                    }

                    @Override
                    public BuildOperationDescriptor.Builder description() {
                        return BuildOperationDescriptor.displayName("Computing dependency verification metadata for " + project.getDisplayName());
                    }
                });
            }
        });
        // We don't need this anymore and because we're going to sort
        // elements to be added for reproducibility, we're freeing memory just in case
        cachedChecksums.clear();
    }

    @Override
    public void onArtifact(ModuleComponentArtifactIdentifier id, File file) {
        addChecksum(id, file, ChecksumKind.sha1);
        addChecksum(id, file, ChecksumKind.sha512);
    }

    private void addChecksum(ModuleComponentArtifactIdentifier id, File file, ChecksumKind kind) {
        ChecksumEntry entry = buildOperationExecutor.call(new CallableBuildOperation<ChecksumEntry>() {
            @Override
            public ChecksumEntry call(BuildOperationContext context) {
                return new ChecksumEntry(id, file, kind, createHash(file, kind));
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return BuildOperationDescriptor.displayName("Generate " + kind + " checksum for " + id);
            }
        });
        synchronized (entriesToBeWritten) {
            entriesToBeWritten.add(entry);
        }
    }

    private String createHash(File file, ChecksumKind kind) {
        return cachedChecksums.computeIfAbsent(new FileChecksum(file, kind), key -> HashUtil.createHash(file, kind.getAlgorithm()).asHexString());
    }

    private static void resolveAllConfigurationsAndForceDownload(Project p) {
        ((ProjectInternal) p).getMutationState().withMutableState(() ->
            p.getConfigurations().all(cnf -> {
                if (((DeprecatableConfiguration) cnf).canSafelyBeResolved()) {
                    try {
                        resolveAndDownloadExternalFiles(cnf);
                    } catch (Exception e) {
                        LOGGER.debug("Cannot resolve configuration {}: {}", cnf.getName(), e.getMessage());
                    }
                }
            })
        );
    }

    private static void resolveAndDownloadExternalFiles(Configuration cnf) {
        cnf.getIncoming().artifactView(MODULE_COMPONENT_FILES).getFiles().getFiles();
    }

    private static class FileChecksum {
        private final File file;
        private final ChecksumKind kind;
        private final int hashCode;

        private FileChecksum(File file, ChecksumKind kind) {
            this.file = file;
            this.kind = kind;
            this.hashCode = precomputeHash();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            FileChecksum that = (FileChecksum) o;

            if (!file.equals(that.file)) {
                return false;
            }
            return kind == that.kind;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        private int precomputeHash() {
            int result = file.hashCode();
            result = 31 * result + kind.hashCode();
            return result;
        }
    }

    private static class ChecksumEntry implements Comparable<ChecksumEntry> {
        private static final Comparator<ChecksumEntry> CHECKSUM_ENTRY_COMPARATOR = Comparator.comparing(ChecksumEntry::getGroup)
            .thenComparing(ChecksumEntry::getModule)
            .thenComparing(ChecksumEntry::getVersion)
            .thenComparing(ChecksumEntry::getFile)
            .thenComparing(ChecksumEntry::getChecksumKind);

        private final ModuleComponentArtifactIdentifier id;
        private final File file;
        private final ChecksumKind checksumKind;
        private final String checksum;
        private final int hashCode;

        private ChecksumEntry(ModuleComponentArtifactIdentifier id, File file, ChecksumKind checksumKind, String checksum) {
            this.id = id;
            this.file = file;
            this.checksumKind = checksumKind;
            this.checksum = checksum;
            this.hashCode = precomputeHashCode();
        }

        private int precomputeHashCode() {
            int result = id.hashCode();
            result = 31 * result + file.getName().hashCode();
            result = 31 * result + checksumKind.hashCode();
            return result;
        }

        String getGroup() {
            return id.getComponentIdentifier().getGroup();
        }

        String getModule() {
            return id.getComponentIdentifier().getModule();
        }

        String getVersion() {
            return id.getComponentIdentifier().getVersion();
        }

        @Override
        public int compareTo(ChecksumEntry other) {
            return CHECKSUM_ENTRY_COMPARATOR.compare(this, other);
        }

        ChecksumKind getChecksumKind() {
            return checksumKind;
        }

        String getFile() {
            return file.getName();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            ChecksumEntry that = (ChecksumEntry) o;

            if (!id.equals(that.id)) {
                return false;
            }
            if (!getFile().equals(that.getFile())) {
                return false;
            }
            return checksumKind == that.checksumKind;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }
}
