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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
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
import org.gradle.internal.hash.ChecksumService;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class WriteDependencyVerificationFile implements DependencyVerificationOverride, ArtifactVerificationOperation {
    private static final Logger LOGGER = LoggerFactory.getLogger(WriteDependencyVerificationFile.class);
    private static final Action<ArtifactView.ViewConfiguration> MODULE_COMPONENT_FILES = conf -> {
        conf.componentFilter(id -> id instanceof ModuleComponentIdentifier);
        conf.setLenient(true);
    };
    private static final Set<String> SUPPORTED_CHECKSUMS = ImmutableSet.of("md5", "sha1", "sha256", "sha512");
    private static final Set<String> SECURE_CHECKSUMS = ImmutableSet.of("sha256", "sha512");
    private static final List<String> DEFAULT_CHECKSUMS = ImmutableList.of("sha1", "sha512");

    private final DependencyVerifierBuilder verificationsBuilder = new DependencyVerifierBuilder();
    private final File gradleDirectory;
    private final BuildOperationExecutor buildOperationExecutor;
    private final List<String> checksums;
    private final Set<ChecksumEntry> entriesToBeWritten = Sets.newLinkedHashSetWithExpectedSize(512);
    private final ChecksumService checksumService;

    public WriteDependencyVerificationFile(File gradleDirectory, BuildOperationExecutor buildOperationExecutor, List<String> checksums, ChecksumService checksumService) {
        this.gradleDirectory = gradleDirectory;
        this.buildOperationExecutor = buildOperationExecutor;
        this.checksums = validateChecksums(checksums);
        this.checksumService = checksumService;
    }

    private List<String> validateChecksums(List<String> checksums) {
        checksums = assertSupportedChecksums(checksums);
        warnAboutInsecureChecksums(checksums);
        return checksums;
    }

    private List<String> assertSupportedChecksums(List<String> checksums) {
        List<String> copy = new ArrayList<>(checksums);
        boolean updated = copy.retainAll(SUPPORTED_CHECKSUMS);
        if (updated) {
            for (String checksum : checksums) {
                if (!SUPPORTED_CHECKSUMS.contains(checksum)) {
                    // we cannot throw an exception at this stage because this happens too early
                    // in the build and the user feedback isn't great ("cannot create service blah!")
                    LOGGER.warn("Invalid checksum type: '" + checksum + "'. You must choose one or more in " + SUPPORTED_CHECKSUMS);
                }
            }
            if (copy.isEmpty()) {
                LOGGER.warn("Falling back to the default checksums: sha1 and sha512");
                copy = DEFAULT_CHECKSUMS;
            }
            return copy;
        }
        return checksums;
    }

    private void warnAboutInsecureChecksums(List<String> checksums) {
        if (checksums.stream().noneMatch(SECURE_CHECKSUMS::contains)) {
            LOGGER.warn("You chose to generate " + checksums.stream().collect(Collectors.joining(" and ")) + " checksums but they are all considered insecure. You should consider adding at least one of " + SECURE_CHECKSUMS.stream().collect(Collectors.joining(" or ")) + ".");
        }
    }

    @Override
    public ModuleComponentRepository overrideDependencyVerification(ModuleComponentRepository original) {
        return new DependencyVerifyingModuleComponentRepository(original, this);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void buildFinished(Gradle gradle) {
        File verifFile = DependencyVerificationOverride.dependencyVerificationsFile(gradleDirectory);
        verifFile.getParentFile().mkdirs();
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
                verificationsBuilder.addChecksum(entry.id, entry.checksumKind, entry.checksum, "Generated by Gradle");
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
    }

    @Override
    public void onArtifact(ModuleComponentArtifactIdentifier id, File file) {
        for (String checksum : checksums) {
            addChecksum(id, file, ChecksumKind.valueOf(checksum));
        }
    }

    private void addChecksum(ModuleComponentArtifactIdentifier id, File file, ChecksumKind kind) {
        ChecksumEntry entry = buildOperationExecutor.call(new CallableBuildOperation<ChecksumEntry>() {
            @Override
            public ChecksumEntry call(BuildOperationContext context) {
                if (file.exists()) {
                    return new ChecksumEntry(id, file, kind, createHash(file, kind));
                } else {
                    LOGGER.warn("Cannot compute checksum for " + file + " because it doesn't exist. It may indicate a corrupt or tampered cache.");
                }
                return null;
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return BuildOperationDescriptor.displayName("Generate " + kind + " checksum for " + id);
            }
        });
        if (entry != null) {
            synchronized (entriesToBeWritten) {
                entriesToBeWritten.add(entry);
            }
        }
    }

    private String createHash(File file, ChecksumKind kind) {
        return checksumService.hash(file, kind.getAlgorithm()).toString();
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

    private static class ChecksumEntry implements Comparable<ChecksumEntry> {
        private static final Comparator<ChecksumEntry> CHECKSUM_ENTRY_COMPARATOR = Comparator.comparing(ChecksumEntry::getGroup)
            .thenComparing(ChecksumEntry::getModule)
            .thenComparing(ChecksumEntry::getVersion)
            .thenComparing(ChecksumEntry::getFile)
            .thenComparing(ChecksumEntry::getChecksumKind)
            .thenComparing(ChecksumEntry::getChecksum);

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
            result = 31 * result + checksum.hashCode();
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

        String getChecksum() {
            return checksum;
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
            if (!checksum.equals(that.checksum)) {
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
