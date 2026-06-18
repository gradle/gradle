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
package org.gradle.api.internal.artifacts.verification.verifier;

import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.gradle.internal.logging.text.TreeFormatter;
import org.gradle.security.internal.PGPUtils;
import org.gradle.security.internal.PublicKeyResultBuilder;
import org.gradle.security.internal.PublicKeyService;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public class SignatureVerificationFailure extends AbstractVerificationFailure {
    private final Map<String, SignatureError> errors;
    private final PublicKeyService keyService;
    private final File signatureFile;
    private final TrustedKeys otherTrustedKeys;

    public SignatureVerificationFailure(File affectedFile, File signatureFile, Map<String, SignatureError> errors, PublicKeyService keyService) {
        this(affectedFile, signatureFile, errors, keyService, TrustedKeys.empty());
    }

    /**
     * Creates a failure that also reports the other keys already trusted for the artifact's {@code group} or
     * {@code group:module}, enriching {@code MISSING_KEY} messages so a rotated signing key can be told apart
     * from a group/module being trusted for the first time.
     *
     * @param otherTrustedKeys the other trusted keys, excluding the key that triggered this failure
     */
    public SignatureVerificationFailure(File affectedFile, File signatureFile, Map<String, SignatureError> errors, PublicKeyService keyService,
                                        TrustedKeys otherTrustedKeys) {
        super(affectedFile);
        this.errors = errors;
        this.keyService = keyService;
        this.signatureFile = signatureFile;
        this.otherTrustedKeys = otherTrustedKeys;
    }

    @Override
    public File getSignatureFile() {
        return signatureFile;
    }

    public Map<String, SignatureError> getErrors() {
        return errors;
    }

    @Override
    public void explainTo(TreeFormatter formatter) {
        if (errors.size() == 1) {
            Map.Entry<String, SignatureError> entry = errors.entrySet().iterator().next();
            formatter.append(toMessage(entry.getKey(), entry.getValue()));
            return;
        }
        formatter.append("Multiple signature verification errors found");
        formatter.startChildren();
        errors.entrySet()
            .stream()
            .sorted(Map.Entry.comparingByKey())
            .forEachOrdered(entry -> formatter.node(toMessage(entry.getKey(), entry.getValue())));
        formatter.endChildren();
    }

    private String toMessage(String key, SignatureError value) {
        StringBuilder sb = new StringBuilder();
        appendError(key, value, sb);
        return sb.toString();
    }

    private void appendError(String keyId, SignatureError error, StringBuilder sb) {
        sb.append("Artifact was signed with key '").append(keyId).append("' ");
        PGPPublicKey publicKey = error.publicKey;
        switch (error.kind) {
            case PASSED_NOT_TRUSTED:
                appendKeyDetails(sb, publicKey);
                sb.append("and passed verification but the key isn't in your trusted keys list.");
                break;
            case FAILED:
                appendKeyDetails(sb, publicKey);
                sb.append("but signature didn't match");
                break;
            case MISSING_KEY:
                sb.append("but it wasn't found in any key server so it couldn't be verified");
                sb.append(otherTrustedKeysNote());
                break;
            default:
                break;
        }
    }

    /**
     * A space-prefixed parenthesised note about the other keys already trusted for the failing artifact's
     * {@code group:module} and/or {@code group}, safe to append after a message and shared by the console and
     * HTML reporters. Empty when there is no trust context for the artifact; otherwise reports the per-scope
     * counts, or states explicitly that none are trusted.
     */
    public String otherTrustedKeysNote() {
        int moduleCount = otherTrustedKeys.moduleScopedKeyIds.size();
        int groupCount = otherTrustedKeys.groupScopedKeyIds.size();
        if (otherTrustedKeys.group == null) {
            // No <trusted-keys> context for this artifact.
            return "";
        }
        if (moduleCount == 0 && groupCount == 0) {
            StringBuilder none = new StringBuilder(" (no other keys are already trusted for ");
            if (otherTrustedKeys.module != null) {
                none.append("module '").append(otherTrustedKeys.group).append(':').append(otherTrustedKeys.module).append("' or ");
            }
            return none.append("group '").append(otherTrustedKeys.group).append("')").toString();
        }
        StringBuilder sb = new StringBuilder(" (");
        if (moduleCount > 0) {
            sb.append(otherKeys(moduleCount))
                .append(" already trusted for module '").append(otherTrustedKeys.group).append(':').append(otherTrustedKeys.module).append('\'');
        }
        if (groupCount > 0) {
            if (moduleCount > 0) {
                sb.append("; ");
            }
            sb.append(otherKeys(groupCount))
                .append(" already trusted for group '").append(otherTrustedKeys.group).append('\'');
        }
        return sb.append(')').toString();
    }

    private static String otherKeys(int count) {
        return count == 1 ? "1 other key is" : count + " other keys are";
    }

    public enum FailureKind {
        PASSED_NOT_TRUSTED,
        FAILED,
        IGNORED_KEY,
        MISSING_KEY
    }

    public void appendKeyDetails(StringBuilder sb, PGPPublicKey key) {
        keyService.findByFingerprint(key.getFingerprint(), new PublicKeyResultBuilder() {
            @Override
            public void keyRing(PGPPublicKeyRing keyring) {
                Set<String> userIds = new TreeSet<>();
                collectUserIds(userIds, key);
                keyring.getPublicKeys().forEachRemaining(userkey -> collectUserIds(userIds, userkey));
                if (!userIds.isEmpty()) {
                    sb.append("(");
                }
                sb.append(String.join(", ", userIds));
                if (!userIds.isEmpty()) {
                    sb.append(") ");
                }
            }

            @Override
            public void publicKey(PGPPublicKey publicKey) {

            }
        });
    }

    private void collectUserIds(Set<String> userIds, PGPPublicKey userkey) {
        userIds.addAll(PGPUtils.getUserIDs(userkey));
    }

    public static class SignatureError {
        private final PGPPublicKey publicKey;
        private final FailureKind kind;

        public SignatureError(@Nullable PGPPublicKey key, FailureKind kind) {
            this.publicKey = key;
            this.kind = kind;
        }

        public FailureKind getKind() {
            return kind;
        }

        public PGPPublicKey getPublicKey() {
            return publicKey;
        }
    }

    /**
     * The other keys already trusted via {@code <trusted-keys>} for a failing artifact, split by whether they
     * are trusted for the whole {@code group} or only the specific {@code group:module}. Drives
     * {@link #otherTrustedKeysNote()}.
     *
     * @see <a href="https://github.com/gradle/gradle/issues/20100">gradle/gradle#20100</a>
     */
    @NullMarked
    public static final class TrustedKeys {
        private final @Nullable String group;
        private final @Nullable String module;
        private final Set<String> groupScopedKeyIds;
        private final Set<String> moduleScopedKeyIds;

        public TrustedKeys(@Nullable String group, @Nullable String module, Set<String> groupScopedKeyIds, Set<String> moduleScopedKeyIds) {
            this.group = group;
            this.module = module;
            this.groupScopedKeyIds = groupScopedKeyIds;
            this.moduleScopedKeyIds = moduleScopedKeyIds;
        }

        public static TrustedKeys empty() {
            return new TrustedKeys(null, null, Set.of(), Set.of());
        }

        public Set<String> getGroupScopedKeyIds() {
            return groupScopedKeyIds;
        }

        public Set<String> getModuleScopedKeyIds() {
            return moduleScopedKeyIds;
        }

        /**
         * Returns a copy without the keys that triggered the current failure, so the note only mentions the
         * <em>other</em> trusted keys. Matching is by case-insensitive suffix, since a failing key id can be a
         * shorter (long) id than the stored fingerprint.
         */
        TrustedKeys excludingFailingKeys(Set<String> failingKeyIds) {
            if (groupScopedKeyIds.isEmpty() && moduleScopedKeyIds.isEmpty()) {
                return this;
            }
            Set<String> failingUpper = new LinkedHashSet<>(failingKeyIds.size());
            for (String failing : failingKeyIds) {
                failingUpper.add(failing.toUpperCase(Locale.ROOT));
            }
            Set<String> moduleKeys = withoutFailingKeys(moduleScopedKeyIds, failingUpper);
            Set<String> groupKeys = withoutFailingKeys(groupScopedKeyIds, failingUpper);
            return new TrustedKeys(group, module, groupKeys, moduleKeys);
        }

        private static Set<String> withoutFailingKeys(Set<String> candidates, Set<String> failingUpper) {
            Set<String> result = new LinkedHashSet<>(candidates.size());
            for (String keyId : candidates) {
                boolean failing = false;
                for (String failingKey : failingUpper) {
                    if (keyId.endsWith(failingKey)) {
                        failing = true;
                        break;
                    }
                }
                if (!failing) {
                    result.add(keyId);
                }
            }
            return result;
        }
    }
}
