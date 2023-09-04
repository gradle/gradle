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
import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.artifacts.ArtifactView;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.artifacts.configurations.ResolutionStrategyInternal;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.DependencyVerifyingModuleComponentRepository;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ModuleComponentRepository;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.ArtifactVerificationOperation;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.DefaultKeyServers;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.DependencyVerificationOverride;
import org.gradle.api.internal.artifacts.verification.exceptions.DependencyVerificationException;
import org.gradle.api.internal.artifacts.verification.model.ChecksumKind;
import org.gradle.api.internal.artifacts.verification.model.IgnoredKey;
import org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationsXmlReader;
import org.gradle.api.internal.artifacts.verification.serializer.DependencyVerificationsXmlWriter;
import org.gradle.api.internal.artifacts.verification.signatures.BuildTreeDefinedKeys;
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
import org.gradle.internal.component.external.model.ModuleComponentGraphResolveState;
import org.gradle.internal.deprecation.DeprecatableConfiguration;
import org.gradle.internal.hash.ChecksumService;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationDescriptor;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationQueue;
import org.gradle.internal.operations.RunnableBuildOperation;
import org.gradle.security.internal.Fingerprint;
import org.gradle.security.internal.PGPUtils;
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
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static com.google.common.io.Files.getNameWithoutExtension;

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
    private static final String TEXT_KEYRING_FORMAT = "text";
    private static final String GPG_KEYRING_FORMAT = "gpg";

    private final DependencyVerifierBuilder verificationsBuilder = new DependencyVerifierBuilder();
    private final BuildOperationExecutor buildOperationExecutor;
    private final List<String> checksums;
    private final Set<VerificationEntry> entriesToBeWritten = Sets.newLinkedHashSetWithExpectedSize(512);
    private final ChecksumService checksumService;
    private final File verificationFile;
    private final BuildTreeDefinedKeys keyrings;
    private final SignatureVerificationServiceFactory signatureVerificationServiceFactory;
    private final boolean isDryRun;
    private final boolean generatePgpInfo;
    private final boolean isExportKeyring;

    private boolean hasMissingSignatures = false;
    private boolean hasMissingKeys = false;
    private boolean hasFailedVerification = false;

    public WriteDependencyVerificationFile(
        File verificationFile,
        BuildTreeDefinedKeys keyrings,
        BuildOperationExecutor buildOperationExecutor,
        List<String> checksums,
        ChecksumService checksumService,
        SignatureVerificationServiceFactory signatureVerificationServiceFactory,
        boolean isDryRun,
        boolean exportKeyRing
    ) {
        this.buildOperationExecutor = buildOperationExecutor;
        this.checksums = checksums;
        this.checksumService = checksumService;
        this.verificationFile = verificationFile;
        this.keyrings = keyrings;
        this.signatureVerificationServiceFactory = signatureVerificationServiceFactory;
        this.isDryRun = isDryRun;
        this.generatePgpInfo = checksums.contains(PGP);
        this.isExportKeyring = exportKeyRing;
    }

    private boolean isWriteVerificationFile() {
        return !checksums.isEmpty();
    }

    private void validateChecksums() {
        if (isWriteVerificationFile()) {
            assertSupportedChecksums();
            warnAboutInsecureChecksums();
        }
    }

    private void assertSupportedChecksums() {
        for (String checksum : checksums) {
            if (!SUPPORTED_CHECKSUMS.contains(checksum)) {
                // we cannot throw an exception at this stage because this happens too early
                // in the build and the user feedback isn't great ("cannot create service blah!")
                LOGGER.warn("Invalid checksum type: '" + checksum + "'. You must choose one or more in " + SUPPORTED_CHECKSUMS);
            }
        }
        assertPgpHasChecksumFallback(checksums);
    }

    private void assertPgpHasChecksumFallback(List<String> kinds) {
        if (kinds.size() == 1 && PGP.equals(kinds.get(0))) {
            throw new DependencyVerificationException("Generating a file with signature verification requires at least one checksum type (sha256 or sha512) as fallback.");
        }
    }

    private void warnAboutInsecureChecksums() {
        if (checksums.stream().noneMatch(SECURE_CHECKSUMS::contains)) {
            LOGGER.warn("You chose to generate " + String.join(" and ", checksums) + " checksums but they are all considered insecure. You should consider adding at least one of " + String.join(" or ", SECURE_CHECKSUMS) + ".");
        }
    }

    @Override
    public ModuleComponentRepository<ModuleComponentGraphResolveState> overrideDependencyVerification(ModuleComponentRepository<ModuleComponentGraphResolveState> original, String resolveContextName, ResolutionStrategyInternal resolutionStrategy) {
        return new DependencyVerifyingModuleComponentRepository(original, this, generatePgpInfo);
    }

    @Override
    public void buildFinished(GradleInternal gradle) {
        ensureOutputDirCreated();
        maybeReadExistingFile();
        // when we generate the verification file, we intentionally ignore if the "use key servers" flag is false
        // because otherwise it forces the user to remove the option in the XML file, generate, then switch it back.
        boolean offline = gradle.getStartParameter().isOffline();
        SignatureVerificationService signatureVerificationService = signatureVerificationServiceFactory.create(
            keyrings,
            DefaultKeyServers.getOrDefaults(verificationsBuilder.getKeyServers()),
            !offline
        );
        if (!verificationsBuilder.isUseKeyServers() && !offline) {
            LOGGER.lifecycle("Will use key servers to download missing keys. If you really want to ignore key servers when generating the verification file, you can use the --offline flag in addition");
        }
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

    public boolean ensureOutputDirCreated() {
        return verificationFile.getParentFile().mkdirs();
    }

    private void serializeResult(SignatureVerificationService signatureVerificationService) throws IOException {
        File out = isDryRun
            ? dryRunVerificationFile()
            : verificationFile;
        if (generatePgpInfo) {
            verificationsBuilder.setVerifySignatures(true);
        }
        DependencyVerifier verifier = verificationsBuilder.build();
        if (isWriteVerificationFile()) {
            DependencyVerificationsXmlWriter.serialize(
                verifier,
                new FileOutputStream(out)
            );
        }
        if (isExportKeyring) {
            exportKeys(signatureVerificationService, verifier);
        }
    }

    private File dryRunVerificationFile() {
        return new File(verificationFile.getParent(), getNameWithoutExtension(verificationFile.getName()) + ".dryrun.xml");
    }

    private void exportKeys(SignatureVerificationService signatureVerificationService, DependencyVerifier verifier) throws IOException {
        BuildTreeDefinedKeys keys = isDryRun ? keyrings.dryRun() : keyrings;
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
        exportKeyRingCollection(signatureVerificationService.getPublicKeyService(), keys, keysToExport);
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
                String origin = "Generated by Gradle";
                String reason = null;
                if (pgpEntry != null) {
                    if (pgpEntry.isFailed()) {
                        hasFailedVerification = true;
                        reason = "PGP signature verification failed!";
                    } else {
                        if (pgpEntry.hasSignatureFile()) {
                            hasMissingKeys = true;
                            reason = "A key couldn't be downloaded";
                        } else {
                            hasMissingSignatures = true;
                            reason = "Artifact is not signed";
                        }
                    }
                }
                verificationsBuilder.addChecksum(entry.id, checksum.getChecksumKind(), checksum.getChecksum(), origin, reason);
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
        buildOperationExecutor.runAllWithAccessToProjectState(queue -> {
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
                    continue;
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
        return kind == ArtifactKind.METADATA && !verificationsBuilder.isVerifyMetadata();
    }

    private void addChecksum(ModuleComponentArtifactIdentifier id, ArtifactKind artifactKind, File file, ChecksumKind kind) {
        ChecksumEntry e = new ChecksumEntry(id, artifactKind, file, kind);
        synchronized (entriesToBeWritten) {
            entriesToBeWritten.add(e);
        }
    }

    private boolean isTrustedArtifact(ModuleComponentArtifactIdentifier id) {
        return verificationsBuilder.getTrustedArtifacts().stream().anyMatch(artifact -> artifact.matches(id));
    }

    private String createHash(File file, ChecksumKind kind) {
        try {
            return checksumService.hash(file, kind.getAlgorithm()).toString();
        } catch (Exception e) {
            LOGGER.debug("Error while snapshotting " + file, e);
            return null;
        }
    }

    private static void resolveAllConfigurationsAndForceDownload(Project project) {
        ((ProjectInternal) project).getOwner().applyToMutableState(p ->
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

    private void exportKeyRingCollection(PublicKeyService publicKeyService, BuildTreeDefinedKeys keyrings, Set<String> publicKeys) throws IOException {
        List<PGPPublicKeyRing> existingRings = loadExistingKeyRing(keyrings);
        PGPPublicKeyRingListBuilder builder = new PGPPublicKeyRingListBuilder();
        for (String publicKey : publicKeys) {
            if (publicKey.length() <= 16) {
                publicKeyService.findByLongId(new BigInteger(publicKey, 16).longValue(), builder);
            } else {
                publicKeyService.findByFingerprint(Fingerprint.fromString(publicKey).getBytes(), builder);
            }
        }

        Stream<PGPPublicKeyRing> keysSeenInVerifier = builder.build()
            .stream()
            .filter(keyring -> PGPUtils.getSize(keyring) != 0);

        Collection<PGPPublicKeyRing> allKeyRings = uniqueKeyRings(Stream.concat(keysSeenInVerifier, existingRings.stream()));

        File asciiArmoredFile = keyrings.getAsciiKeyringsFile();
        File keyringFile = keyrings.getBinaryKeyringsFile();

        String keyRingFormat = verificationsBuilder.getKeyRingFormat();
        if (keyRingFormat.equals(TEXT_KEYRING_FORMAT)) {
            writeAsciiArmoredKeyRingFile(asciiArmoredFile, allKeyRings);
            LOGGER.lifecycle("Exported {} keys to {}", allKeyRings.size(), asciiArmoredFile);
        } else if (keyRingFormat.equals(GPG_KEYRING_FORMAT)) {
            writeBinaryKeyringFile(keyringFile, allKeyRings);
            LOGGER.lifecycle("Exported {} keys to {}", allKeyRings.size(), keyringFile);
        } else {
            writeAsciiArmoredKeyRingFile(asciiArmoredFile, allKeyRings);
            writeBinaryKeyringFile(keyringFile, allKeyRings);
            LOGGER.lifecycle("Exported {} keys to {} and {}", allKeyRings.size(), keyringFile, asciiArmoredFile);
        }
    }

    private static Collection<PGPPublicKeyRing> uniqueKeyRings(Stream<PGPPublicKeyRing> keyRings) {
        SortedMap<Long, PGPPublicKeyRing> seenKeyIds = new TreeMap<>();
        keyRings.forEach(keyRing -> {
            Long keyId = keyRing.getPublicKey().getKeyID();
            PGPPublicKeyRing current = seenKeyIds.get(keyId);
            if (current == null || PGPUtils.getSize(current) < PGPUtils.getSize(keyRing)) {
                seenKeyIds.put(keyId, keyRing);
            }
        });
        return seenKeyIds.values();
    }

    private void writeAsciiArmoredKeyRingFile(File ascii, Collection<PGPPublicKeyRing> allKeyRings) throws IOException {
        if (ascii.exists()) {
            ascii.delete();
        }
        boolean hasKey = false;
        for (PGPPublicKeyRing keyRing : allKeyRings) {
            // First let's write some human readable info about the keyring being serialized
            try (OutputStream out = new FileOutputStream(ascii, true)) {
                if (hasKey) {
                    out.write('\n');
                }
                Iterator<PGPPublicKey> pks = keyRing.getPublicKeys();
                while (pks.hasNext()) {
                    boolean hasUid = false;
                    PGPPublicKey pk = pks.next();
                    String keyType = pk.isMasterKey() ? "pub" : "sub";
                    out.write((keyType + "    " + SecuritySupport.toLongIdHexString(pk.getKeyID()).toUpperCase() + "\n").getBytes(StandardCharsets.US_ASCII));
                    List<String> userIDs = PGPUtils.getUserIDs(pk);
                    for(String uid : userIDs) {
                        hasUid = true;
                        out.write(("uid    " + uid + "\n").getBytes(StandardCharsets.US_ASCII));
                    }
                    if (hasUid) {
                        out.write('\n');
                    }
                }
            }
            // Then write the ascii armored keyring
            try (FileOutputStream fos = new FileOutputStream(ascii, true);
                 ArmoredOutputStream out = new ArmoredOutputStream(fos)) {
                keyRing.encode(out, true);
            }
            hasKey = true;
        }
    }

    private void writeBinaryKeyringFile(File keyringFile, Collection<PGPPublicKeyRing> allKeyRings) throws IOException {
        try (OutputStream out = new FileOutputStream(keyringFile)) {
            for (PGPPublicKeyRing keyRing : allKeyRings) {
                keyRing.encode(out, true);
            }
        }
    }

    private static class PGPPublicKeyRingListBuilder implements PublicKeyResultBuilder {
        private final ImmutableList.Builder<PGPPublicKeyRing> builder = ImmutableList.builder();

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

    private List<PGPPublicKeyRing> loadExistingKeyRing(BuildTreeDefinedKeys keyrings) throws IOException {
        List<PGPPublicKeyRing> existingRings;
        if (!isDryRun) {
            existingRings = keyrings.loadKeys();
            LOGGER.info("Existing keyring file contains {} keyrings", existingRings.size());
        } else {
            existingRings = Collections.emptyList();
        }
        return existingRings;
    }
}
