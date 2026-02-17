/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.plugins.signing.signatory.internal.pgp;

import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;
import org.gradle.internal.lazy.Lazy;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public abstract class PgpSignatoryService implements BuildService<BuildServiceParameters.None> {
    private static final String SERVICE_NAME = PgpSignatoryService.class.getName();

    public static Provider<PgpSignatoryService> obtain(Project project) {
        return project.getGradle().getSharedServices().registerIfAbsent(SERVICE_NAME, PgpSignatoryService.class);
    }

    private final ConcurrentMap<PgpKeyHandle, ParsedPgpKey> cachedKeys = new ConcurrentHashMap<>();

    public ParsedPgpKey readSecretKeyFromKeyRingFile(String keyId, File file, String password) {
        return cachedKeys.computeIfAbsent(new PgpKeyInKeyringFile(keyId, file, password), handle -> new ParsedPgpKey(PgpSignatoryUtil.readSecretKey(keyId, file), password));
    }

    public ParsedPgpKey readSecretKeyFromArmoredString(@Nullable String keyId, String keyData, String password) {
        return cachedKeys.computeIfAbsent(new PgpKeyData(keyId, keyData, password), handle -> new ParsedPgpKey(PgpSignatoryUtil.parseSecretKey(keyId, keyData), password));
    }

    private interface PgpKeyHandle {}

    private static class PgpKeyInKeyringFile implements PgpKeyHandle {
        final String keyId;
        final File file;
        final String password;

        PgpKeyInKeyringFile(String keyId, File file, String password) {
            this.keyId = keyId;
            this.file = file;
            this.password = password;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof PgpKeyInKeyringFile)) {
                return false;
            }
            PgpKeyInKeyringFile that = (PgpKeyInKeyringFile) o;
            return Objects.equals(keyId, that.keyId) && Objects.equals(file, that.file) && Objects.equals(password, that.password);
        }

        @Override
        public int hashCode() {
            return Objects.hash(keyId, file, password);
        }
    }

    private static class PgpKeyData implements PgpKeyHandle {
        final @Nullable String keyId;
        final String armoredKey;
        final String password;

        PgpKeyData(@Nullable String keyId, String armoredKey, String password) {
            this.keyId = keyId;
            this.armoredKey = armoredKey;
            this.password = password;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof PgpKeyData)) {
                return false;
            }
            PgpKeyData that = (PgpKeyData) o;
            return Objects.equals(keyId, that.keyId) && Objects.equals(armoredKey, that.armoredKey) && Objects.equals(password, that.password);
        }

        @Override
        public int hashCode() {
            return Objects.hash(keyId, armoredKey, password);
        }
    }

    public static class ParsedPgpKey {
        private final PGPSecretKey secretKey;
        private final Lazy<PGPPrivateKey> privateKey;

        private ParsedPgpKey(PGPSecretKey secretKey, String password) {
            this.secretKey = secretKey;
            privateKey = Lazy.locking().of(() -> PgpSignatoryUtil.extractPrivateKey(secretKey, password));
        }

        public PGPSecretKey getSecretKey() {
            return secretKey;
        }

        public PGPPrivateKey getPrivateKey() {
            return privateKey.get();
        }
    }
}
