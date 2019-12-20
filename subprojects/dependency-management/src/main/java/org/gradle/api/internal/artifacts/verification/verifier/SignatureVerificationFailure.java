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

import com.google.common.collect.Sets;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.gradle.security.internal.PublicKeyService;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class SignatureVerificationFailure implements VerificationFailure {
    private final Map<String, SignatureError> errors;
    private final PublicKeyService keyService;

    public SignatureVerificationFailure(Map<String, SignatureError> errors, PublicKeyService keyService) {
        this.errors = errors;
        this.keyService = keyService;
    }

    public Map<String, SignatureError> getErrors() {
        return errors;
    }

    public String getMessage() {
        if (errors.size() == 1) {
            Map.Entry<String, SignatureError> entry = errors.entrySet().iterator().next();
            return toMessage(entry.getKey(), entry.getValue());
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Multiple signature verification errors found: ");
        errors.entrySet()
            .stream()
            .sorted(Map.Entry.comparingByKey())
            .forEachOrdered(entry -> appendError(entry.getKey(), entry.getValue(), sb));
        return sb.toString();
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
                sb.append("(");
                appendKeyDetails(sb, publicKey);
                sb.append(") and passed verification but the key isn't in your trusted keys list.");
                break;
            case KEY_NOT_FOUND:
                sb.append("but it wasn't found in any key server so it couldn't be verified");
                break;
            case FAILED:
                sb.append("but signature didn't match");
                break;
        }
    }

    public enum FailureKind {
        PASSED_NOT_TRUSTED,
        KEY_NOT_FOUND,
        FAILED
    }

    private void appendKeyDetails(StringBuilder sb, PGPPublicKey key) {
        Optional<PGPPublicKeyRing> allKeys = keyService.findKeyRing(key.getKeyID());
        Set<String> userIds = Sets.newTreeSet();
        collectUserIds(userIds, key);
        allKeys.ifPresent(keyring ->{
            keyring.getPublicKeys().forEachRemaining(userkey -> {
                collectUserIds(userIds, userkey);
            });
        });
        sb.append(String.join(", ", userIds));
    }

    private void collectUserIds(Set<String> userIds, PGPPublicKey userkey) {
        userkey.getUserIDs().forEachRemaining(userIds::add);
    }

    public static class SignatureError {
        private final PGPPublicKey publicKey;
        private final FailureKind kind;

        public SignatureError(@Nullable PGPPublicKey key, FailureKind kind) {
            this.publicKey = key;
            this.kind = kind;
        }

        @Nullable
        public PGPPublicKey getPublicKey() {
            return publicKey;
        }

        public FailureKind getKind() {
            return kind;
        }
    }
}
