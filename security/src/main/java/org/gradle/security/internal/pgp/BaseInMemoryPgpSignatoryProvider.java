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

package org.gradle.security.internal.pgp;

import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.jcajce.JcaPGPSecretKeyRing;
import org.bouncycastle.openpgp.jcajce.JcaPGPSecretKeyRingCollection;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.security.internal.BaseSignatoryProvider;
import org.gradle.plugins.signing.signatory.pgp.PgpKeyId;
import org.gradle.plugins.signing.signatory.pgp.PgpSignatory;
import org.gradle.plugins.signing.signatory.pgp.PgpSignatoryFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * A {@link BaseSignatoryProvider} of {@link PgpSignatory} instances read from
 * ascii-armored in-memory secret keys instead of a keyring.
 */
public class BaseInMemoryPgpSignatoryProvider implements BaseSignatoryProvider<PgpSignatory> {

    private final PgpSignatoryFactory factory = new PgpSignatoryFactory();
    private final Map<String, PgpSignatory> signatories = new LinkedHashMap<>();
    private final String defaultKeyId;
    private final String defaultSecretKey;
    private final String defaultPassword;

    public BaseInMemoryPgpSignatoryProvider(String defaultSecretKey, String defaultPassword) {
        this(null, defaultSecretKey, defaultPassword);
    }

    public BaseInMemoryPgpSignatoryProvider(String defaultKeyId, String defaultSecretKey, String defaultPassword) {
        this.defaultKeyId = defaultKeyId;
        this.defaultSecretKey = defaultSecretKey;
        this.defaultPassword = defaultPassword;
    }

    @Override
    public PgpSignatory getDefaultSignatory(Project project) {
        if (defaultSecretKey != null && defaultPassword != null) {
            return createSignatory("default", defaultKeyId, defaultSecretKey, defaultPassword);
        }
        return null;
    }

    @Override
    public PgpSignatory getSignatory(String name) {
        return signatories.get(name);
    }

    protected void addSignatory(String name, String keyId, String secretKey, String password) {
        signatories.put(name, createSignatory(name, keyId, secretKey, password));
    }

    private PgpSignatory createSignatory(String name, String keyId, String secretKey, String password) {
        try (InputStream in = PGPUtil.getDecoderStream(new ByteArrayInputStream(secretKey.getBytes(UTF_8)))) {
            if (keyId == null) {
                PGPSecretKey key = new JcaPGPSecretKeyRing(in).getSecretKey();
                return factory.createSignatory(name, key, password);
            } else {
                PgpKeyId expectedKeyId = new PgpKeyId(keyId);
                for (PGPSecretKeyRing keyring : new JcaPGPSecretKeyRingCollection(in)) {
                    for (PGPSecretKey key : keyring) {
                        if (expectedKeyId.equals(new PgpKeyId(key.getKeyID()))) {
                            return factory.createSignatory(name, key, password);
                        }
                    }
                }
            }
            return null;
        } catch (Exception e) {
            throw new InvalidUserDataException("Could not read PGP secret key", e);
        }
    }

}
