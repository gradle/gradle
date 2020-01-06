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
package org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.writer;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.artifacts.ArtifactView;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.DependencyVerifyingModuleComponentRepository;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepository;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.ArtifactVerificationOperation;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.DefaultKeyServers;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.DependencyVerificationOverride;
import org.gradle.api.internal.artifacts.verification.model.ChecksumKind;
import org.gradle.api.internal.artifacts.verification.model.IgnoredKey;
import org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationsXmlReader;
import org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationsXmlWriter;
import org.gradle.api.internal.artifacts.verification.signatures.SignatureVerificationResultBuilder;
import org.gradle.api.internal.artifacts.verification.signatures.SignatureVerificationService;
import org.gradle.api.internal.artifacts.verification.signatures.SignatureVerificationServiceFactory;
import org.gradle.api.internal.artifacts.verification.verifier.DependencyVerificationConfiguration;
import org.gradle.api.internal.artifacts.verification.verifier.DependencyVerifier;
import org.gradle.api.internal.artifacts.verification.verifier.DependencyVerifierBuilder;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.Factory;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;
import org.gradle.internal.deprecation.DeprecatableConfiguration;
import org.gradle.internal.hash.ChecksumService;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.security.internal.Fingerprint;
import org.gradle.security.internal.PublicKeyResultBuilder;
import org.gradle.security.internal.PublicKeyService;
import org.gradle.security.internal.SecuritySupport;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class WriteDependencyVerificationFile implements DependencyVerificationOverride, ArtifactVerificationOperation {
    private static final Logger LOGGER = Logging.getLogger(WriteDependencyVerificationFile.class);
    private static final Action<ArtifactView.ViewConfiguration> MODULE_COMPONENT_FILES = conf -> {
        conf.componentFilter(id -> id instanceof ModuleComponentIdentifier);
        conf.setLenient(true);
    };
    private static final String PGP = "pgp";
    private static final String MD5 = "md5";
    private static final String SHA1 = "sha1";
    private static final String SHA256 = "sha256";
    private static final String SHA512 = "sha512";
    private static final Set<String> SUPPORTED_CHECKSUMS = ImmutableSet.of(MD5, SHA1, SHA256, SHA512, PGP);
    private static final Set<String> SECURE_CHECKSUMS = ImmutableSet.of(SHA256, SHA512, PGP);
    private static final String PGP_VERIFICATION_FAILED = "PGP verification failed";
    private static final String KEY_NOT_DOWNLOADED = "Key couldn't be downloaded from any key server";

    private final DependencyVerifierBuilder verificationsBuilder = new DependencyVerifierBuilder();
    private final BuildOperationExecutor buildOperationExecutor;
    private final List<String> checksums;
    private final Set<VerificationEntry> entriesToBeWritten = Sets.newLinkedHashSetWithExpectedSize(512);
    private final ChecksumService checksumService;
    private final File verificationFile;
    private final File keyringsFile;
    private final SignatureVerificationServiceFactory signatureVerificationServiceFactory;
    private final boolean isDryRun;
    private final boolean generatePgpInfo;
    private final boolean isExportKeyring;

    private boolean hasMissingSignatures = false;
    private boolean hasMissingKeys = false;
    private boolean hasFailedVerification = false;

    public WriteDependencyVerificationFile(File buildDirectory,
                                           BuildOperationExecutor buildOperationExecutor,
                                           List<String> checksums,
                                           ChecksumService checksumService,
                                           SignatureVerificationServiceFactory signatureVerificationServiceFactory,
                                           boolean isDryRun,
                                           boolean exportKeyRing) {
        this.buildOperationExecutor = buildOperationExecutor;
        this.checksums = checksums;
        this.checksumService = checksumService;
        this.verificationFile = DependencyVerificationOverride.dependencyVerificationsFile(buildDirectory);
        this.keyringsFile = DependencyVerificationOverride.keyringsFile(buildDirectory);
        this.signatureVerificationServiceFactory = signatureVerificationServiceFactory;
        this.isDryRun = isDryRun;
        this.generatePgpInfo = checksums.contains(PGP);
        this.isExportKeyring = exportKeyRing;
    }

    private void validateChecksums() {
        assertSupportedChecksums();
        warnAboutInsecureChecksums();
    }

    private void assertSupportedChecksums() {
        for (String checksum : checksums) {
            if (!SUPPORTED_CHECKSUMS.contains(checksum)) {
                // we cannot throw an exception at this stage because this happens too early
                // in the build and the user feedback isn't great ("cannot create service blah!")
                LOGGER.warn("Invalid checksum type: '" + checksum + "'. You must choose one or more in " + SUPPORTED_CHECKSUMS);
            }
        }
        if (checksums.isEmpty()) {
            throw new InvalidUserDataException("You must specify at least one checksum type to use. You must choose one or more in " + SUPPORTED_CHECKSUMS);
        }
        assertPgpHasChecksumFallback(checksums);
    }

    private void assertPgpHasChecksumFallback(List<String> kinds) {
        if (kinds.size() == 1 && PGP.equals(kinds.get(0))) {
            throw new InvalidUserDataException("Generating a file with signature verification requires at least one checksum type (sha256 or sha512) as fallback.");
        }
    }

    private void warnAboutInsecureChecksums() {
        if (checksums.stream().noneMatch(SECURE_CHECKSUMS::contains)) {
            LOGGER.warn("You chose to generate " + checksums.stream().collect(Collectors.joining(" and ")) + " checksums but they are all considered insecure. You should consider adding at least one of " + SECURE_CHECKSUMS.stream().collect(Collectors.joining(" or ")) + ".");
        }
    }

    @Override
    public ModuleComponentRepository overrideDependencyVerification(ModuleComponentRepository original) {
        return new DependencyVerifyingModuleComponentRepository(original, this, generatePgpInfo);
    }

    @Override
    public void buildFinished(Gradle gradle) {

        maybeReadExistingFile();
        SignatureVerificationService signatureVerificationService = signatureVerificationServiceFactory.create(
            keyringsFile,
            DefaultKeyServers.getOrDefaults(verificationsBuilder.getKeyServers())
        );
        try {
            validateChecksums();
            resolveAllConfigurationsConcurrently(gradle);
            computeChecksumsConcurrently(signatureVerificationService);
            writeEntriesSerially();
            serializeResult(signatureVerificationService);
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        } finally {
            signatureVerificationService.stop();
        }
    }

    private void serializeResult(SignatureVerificationService signatureVerificationService) throws IOException {
        File out = verificationFile;
        if (isDryRun) {
            out = new File(verificationFile.getParent(), Files.getNameWithoutExtension(verificationFile.getName()) + ".dryrun.xml");
        }
        if (generatePgpInfo) {
            verificationsBuilder.setVerifySignatures(true);
        }
        DependencyVerifier verifier = verificationsBuilder.build();
        DependencyVerificationsXmlWriter.serialize(
            verifier,
            new FileOutputStream(out)
        );
        if (isExportKeyring) {
            exportKeys(signatureVerificationService, verifier);
        }
    }

    private void exportKeys(SignatureVerificationService signatureVerificationService, DependencyVerifier verifier) throws IOException {
        String keyringFileName = isDryRun ? VERIFICATION_KEYRING_DRYRUN_GPG : VERIFICATION_KEYRING_GPG;
        File keyringExportFile = new File(verificationFile.getParent(), keyringFileName);
        Set<String> keysToExport = Sets.newHashSet();
        verifier.getConfiguration()
            .getTrustedKeys()
            .stream()
            .map(DependencyVerificationConfiguration.TrustedKey::getKeyId)
            .forEach(keysToExport::add);
        verifier.getConfiguration()
            .getIgnoredKeys()
            .stream()
            .map(IgnoredKey::getKeyId)
            .forEach(keysToExport::add);
        verifier.getVerificationMetadata()
            .stream()
            .flatMap(md -> md.getArtifactVerifications().stream())
            .flatMap(avm -> Stream.concat(avm.getTrustedPgpKeys().stream(), avm.getIgnoredPgpKeys().stream().map(IgnoredKey::getKeyId)))
            .forEach(keysToExport::add);
        exportKeyRingCollection(signatureVerificationService.getPublicKeyService(), keyringExportFile, keysToExport);
    }

    private void maybeReadExistingFile() {
        if (verificationFile.exists()) {
            LOGGER.info("Found dependency verification metadata file, updating");
            try {
                DependencyVerificationsXmlReader.readFromXml(new FileInputStream(verificationFile), verificationsBuilder);
            } catch (FileNotFoundException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private void writeEntriesSerially() {
        AtomicReference<PgpEntry> previousEntry = new AtomicReference<>();
        entriesToBeWritten.stream()
            .sorted()
            .filter(this::shouldWriteEntry)
            .forEachOrdered(e -> registerEntryToBuilder(e, previousEntry));
        printWarnings();
    }

    private void printWarnings() {
        if (hasMissingKeys || hasFailedVerification) {
            StringBuilder sb = new StringBuilder("A verification file was generated but some problems were discovered:\n");
            if (hasMissingSignatures) {
                sb.append("   - some artifacts aren't signed or the signature couldn't be retrieved.");
                sb.append("\n");
            }
            if (hasMissingKeys) {
                sb.append("   - some keys couldn't be downloaded. They were automatically added as ignored keys but you should review if this is acceptable. Look for entries with the following comment: ");
                sb.append(KEY_NOT_DOWNLOADED);
                sb.append("\n");
            }
            if (hasFailedVerification) {
                sb.append("   - some signature verification failed. Checksums were generated for those artifacts but you MUST check if there's an actual problem. Look for entries with the following comment: ");
                sb.append(PGP_VERIFICATION_FAILED);
                sb.append("\n");
            }
            LOGGER.warn(sb.toString());
        }
    }

    private void registerEntryToBuilder(VerificationEntry entry, AtomicReference<PgpEntry> previousEntry) {
        // checksums are written _after_ PGP, so if the previous entry was PGP and
        // that it matches the artifact id we don't always need to write the checksum
        PgpEntry pgpEntry = previousEntry.get();
        if (pgpEntry != null && !pgpEntry.id.equals(entry.id)) {
            // previous entry was on unrelated module
            pgpEntry = null;
            previousEntry.set(null);
        }
        if (entry instanceof ChecksumEntry) {
            ChecksumEntry checksum = (ChecksumEntry) entry;
            if (pgpEntry == null || (entry.id.equals(pgpEntry.id) && pgpEntry.isRequiringChecksums())) {
                String label = "Generated by Gradle";
                if (pgpEntry != null) {
                    if (pgpEntry.isFailed()) {
                        hasFailedVerification = true;
                        label += " because PGP signature verification failed!";
                    } else {
                        if (pgpEntry.hasSignatureFile()) {
                            hasMissingKeys = true;
                            label += " because a key couldn't be downloaded";
                        } else {
                            hasMissingSignatures = true;
                            label += " because artifact wasn't signed";
                        }
                    }
                }
                verificationsBuilder.addChecksum(entry.id, checksum.getChecksumKind(), checksum.getChecksum(), label);
            }
        } else {
            PgpEntry pgp = (PgpEntry) entry;
            previousEntry.set(pgp);
            Set<String> failedKeys = Sets.newTreeSet(pgp.getFailed());
            for (String failedKey : failedKeys) {
                verificationsBuilder.addIgnoredKey(pgp.id, new IgnoredKey(failedKey, PGP_VERIFICATION_FAILED));
            }
            if (pgp.hasArtifactLevelKeys()) {
                for (String key : pgp.getArtifactLevelKeys()) {
                    if (!failedKeys.contains(key)) {
                        verificationsBuilder.addTrustedKey(pgp.id, key);
                    }
                }
            }
        }
    }

    private boolean shouldWriteEntry(VerificationEntry entry) {
        if (entry instanceof ChecksumEntry) {
            return ((ChecksumEntry) entry).getChecksum() != null && !isTrustedArtifact(entry.id);
        }
        return !isTrustedArtifact(entry.id);
    }

    private void resolveAllConfigurationsConcurrently(Gradle gradle) {
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
                        String displayName = "Resolving configurations of " + project.getDisplayName();
                        return BuildOperationDescriptor.displayName(displayName)
                            .progressDisplayName(displayName);
                    }
                });
            }
        });
    }

    private void computeChecksumsConcurrently(SignatureVerificationService signatureVerificationService) {
        Set<String> collectedIgnoredKeys = generatePgpInfo ? Sets.newConcurrentHashSet() : null;
        buildOperationExecutor.runAll(queue -> {
            for (VerificationEntry entry : entriesToBeWritten) {
                if (shouldSkipVerification(entry.getArtifactKind())) {
                    continue;
                }
                if (!entry.getFile().exists()) {
                    LOGGER.warn("Cannot compute checksum for " + entry.getFile() + " because it doesn't exist. It may indicate a corrupt or tampered cache.");
                }
                if (entry instanceof ChecksumEntry) {
                    queueChecksumVerification(queue, (ChecksumEntry) entry);
                } else {
                    queueSignatureVerification(queue, signatureVerificationService, (PgpEntry) entry, collectedIgnoredKeys);
                }
            }
        });
        if (generatePgpInfo) {
            postProcessPgpResults(collectedIgnoredKeys);
        }
    }

    private void postProcessPgpResults(Set<String> collectedIgnoredKeys) {
        for (String ignoredKey : collectedIgnoredKeys) {
            verificationsBuilder.addIgnoredKey(new IgnoredKey(ignoredKey, KEY_NOT_DOWNLOADED));
        }
        PgpKeyGrouper grouper = new PgpKeyGrouper(verificationsBuilder, entriesToBeWritten);
        grouper.performPgpKeyGrouping();
    }

    private void queueSignatureVerification(BuildOperationQueue<RunnableBuildOperation> queue, SignatureVerificationService signatureVerificationService, PgpEntry entry, Set<String> ignoredKeys) {
        queue.add(new RunnableBuildOperation() {
            @Override
            public void run(BuildOperationContext context) {
                File signature = entry.getSignatureFile().create();
                if (signature != null) {
                    SignatureVerificationResultBuilder builder = new WriterSignatureVerificationResult(ignoredKeys, entry);
                    signatureVerificationService.verify(entry.file, signature, Collections.emptySet(), Collections.emptySet(), builder);
                }
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return BuildOperationDescriptor.displayName("Verifying dependency signature")
                    .progressDisplayName("Verifying signature of " + entry.id);
            }
        });
    }

    private void queueChecksumVerification(BuildOperationQueue<RunnableBuildOperation> queue, ChecksumEntry entry) {
        queue.add(new RunnableBuildOperation() {
            @Override
            public void run(BuildOperationContext context) {
                entry.setChecksum(createHash(entry.getFile(), entry.getChecksumKind()));
            }

            @Override
            public BuildOperationDescriptor.Builder description() {
                return BuildOperationDescriptor.displayName("Computing checksums")
                    .progressDisplayName("Computing checksum of " + entry.id);
            }
        });
    }

    @Override
    public void onArtifact(ArtifactKind kind, ModuleComponentArtifactIdentifier id, File mainFile, Factory<File> signatureFile, String repositoryName, String repositoryId) {
        for (String checksum : checksums) {
            if (PGP.equals(checksum)) {
                addPgp(id, kind, mainFile, signatureFile);
            } else {
                addChecksum(id, kind, mainFile, ChecksumKind.valueOf(checksum));
            }
        }
    }

    private void addPgp(ModuleComponentArtifactIdentifier id, ArtifactKind kind, File mainFile, Factory<File> signatureFile) {
        PgpEntry entry = new PgpEntry(id, kind, mainFile, signatureFile);
        synchronized (entriesToBeWritten) {
            entriesToBeWritten.add(entry);
        }
    }

    private boolean shouldSkipVerification(ArtifactVerificationOperation.ArtifactKind kind) {
        if (kind == ArtifactVerificationOperation.ArtifactKind.METADATA && !verificationsBuilder.isVerifyMetadata()) {
            return true;
        }
        return false;
    }

    private void addChecksum(ModuleComponentArtifactIdentifier id, ArtifactKind artifactKind, File file, ChecksumKind kind) {
        ChecksumEntry e = new ChecksumEntry(id, artifactKind, file, kind);
        synchronized (entriesToBeWritten) {
            entriesToBeWritten.add(e);
        }
    }

    private boolean isTrustedArtifact(ModuleComponentArtifactIdentifier id) {
        if (verificationsBuilder.getTrustedArtifacts().stream().anyMatch(artifact -> artifact.matches(id))) {
            return true;
        }
        return false;
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

    private void exportKeyRingCollection(PublicKeyService publicKeyService, File keyringFile, Set<String> publicKeys) throws IOException {
        List<PGPPublicKeyRing> existingRings = loadExistingKeyRing(keyringFile);
        PGPPublicKeyRingListBuilder builder = new PGPPublicKeyRingListBuilder();
        for (String publicKey : publicKeys) {
            if (publicKey.length() <= 16) {
                publicKeyService.findByLongId(new BigInteger(publicKey, 16).longValue(), builder);
            } else {
                publicKeyService.findByFingerprint(Fingerprint.fromString(publicKey).getBytes(), builder);
            }
        }

        List<PGPPublicKeyRing> keysSeenInVerifier = builder.build()
            .stream()
            .filter(WriteDependencyVerificationFile::hasAtLeastOnePublicKey)
            .filter(e -> existingRings.stream().noneMatch(ring -> keyIds(ring).equals(keyIds(e))))
            .collect(Collectors.toList());
        ImmutableList<PGPPublicKeyRing> allKeyRings = ImmutableList.<PGPPublicKeyRing>builder()
            .addAll(existingRings)
            .addAll(keysSeenInVerifier)
            .build();
        try (OutputStream out = new FileOutputStream(keyringFile)) {
            for (PGPPublicKeyRing keyRing : allKeyRings) {
                keyRing.encode(out, true);
            }
        }
        LOGGER.lifecycle("Exported {} keys to {}", allKeyRings.size(), keyringFile);
    }

    private static class PGPPublicKeyRingListBuilder implements PublicKeyResultBuilder {
        private final ImmutableList.Builder<PGPPublicKeyRing> builder = ImmutableList.builder();

        @Override
        public void keyRing(PGPPublicKeyRing keyring) {
            builder.add(keyring);
        }

        @Override
        public void publicKey(PGPPublicKey publicKey) {

        }

        public List<PGPPublicKeyRing> build() {
            return builder.build();
        }
    }

    private static boolean hasAtLeastOnePublicKey(PGPPublicKeyRing ring) {
        return ring.getPublicKeys().hasNext();
    }

    private List<PGPPublicKeyRing> loadExistingKeyRing(File keyringFile) throws IOException {
        List<PGPPublicKeyRing> existingRings;
        if (!isDryRun && keyringFile.exists()) {
            existingRings = SecuritySupport.loadKeyRingFile(keyringFile);
            LOGGER.info("Existing keyring file contains {} keyrings", existingRings.size());
        } else {
            existingRings = Collections.emptyList();
        }
        return existingRings;
    }

    private static Set<Long> keyIds(PGPPublicKeyRing ring) {
        return ImmutableList.copyOf(ring.getPublicKeys()).stream().map(PGPPublicKey::getKeyID).collect(Collectors.toSet());
    }
}
