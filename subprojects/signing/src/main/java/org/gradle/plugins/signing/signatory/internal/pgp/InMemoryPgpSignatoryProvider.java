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

package org.gradle.plugins.signing.signatory.internal.pgp;

import groovy.lang.Closure;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.jcajce.JcaPGPSecretKeyRing;
import org.bouncycastle.openpgp.jcajce.JcaPGPSecretKeyRingCollection;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.plugins.signing.SigningExtension;
import org.gradle.plugins.signing.signatory.SignatoryProvider;
import org.gradle.plugins.signing.signatory.pgp.PgpKeyId;
import org.gradle.plugins.signing.signatory.pgp.PgpSignatory;
import org.gradle.plugins.signing.signatory.pgp.PgpSignatoryFactory;
import org.gradle.util.ConfigureUtil;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.codehaus.groovy.runtime.DefaultGroovyMethods.asType;

/**
 * A {@link SignatoryProvider} of {@link PgpSignatory} instances read from
 * ascii-armored in-memory secret keys instead of a keyring.
 */
public class InMemoryPgpSignatoryProvider implements SignatoryProvider<PgpSignatory> {

    private final PgpSignatoryFactory factory = new PgpSignatoryFactory();
    private final Map<String, PgpSignatory> signatories = new LinkedHashMap<>();
    private final String defaultKeyId;
    private final String defaultSecretKey;
    private final String defaultPassword;

    public InMemoryPgpSignatoryProvider(String defaultSecretKey, String defaultPassword) {
        this(null, defaultSecretKey, defaultPassword);
    }

    public InMemoryPgpSignatoryProvider(String defaultKeyId, String defaultSecretKey, String defaultPassword) {
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

    @SuppressWarnings("unused") // invoked by Groovy
    public PgpSignatory propertyMissing(String signatoryName) {
        return getSignatory(signatoryName);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void configure(SigningExtension settings, Closure closure) {
        ConfigureUtil.configure(closure, new Object() {
            @SuppressWarnings("unused") // invoked by Groovy
            public void methodMissing(String name, Object args) {
                createSignatoryFor(name, asType(args, Object[].class));
            }
        });
    }

    private void createSignatoryFor(String name, Object[] args) {
        String keyId = null;
        String secretKey = null;
        String password = null;

        switch (args.length) {
            case 2:
                secretKey = args[0].toString();
                password = args[1].toString();
                break;
            case 3:
                keyId = args[0].toString();
                secretKey = args[1].toString();
                password = args[2].toString();
                break;
            default:
                throw new IllegalArgumentException("Invalid args (" + name + ": " + Arrays.toString(args) + ")");
        }
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
