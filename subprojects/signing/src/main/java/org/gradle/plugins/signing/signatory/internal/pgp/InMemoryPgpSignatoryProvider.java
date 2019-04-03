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
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.jcajce.JcaPGPSecretKeyRing;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.plugins.signing.SigningExtension;
import org.gradle.plugins.signing.signatory.SignatoryProvider;
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
    private final String defaultSecretKey;
    private final String defaultPassword;

    public InMemoryPgpSignatoryProvider(String defaultSecretKey, String defaultPassword) {
        this.defaultSecretKey = defaultSecretKey;
        this.defaultPassword = defaultPassword;
    }

    @Override
    public PgpSignatory getDefaultSignatory(Project project) {
        if (defaultSecretKey != null && defaultPassword != null) {
            return createSignatory("default", defaultSecretKey, defaultPassword);
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
        if (args.length != 2) {
            throw new IllegalArgumentException("Invalid args (" + name + ": " + Arrays.toString(args) + ")");
        }
        String secretKey = args[0].toString();
        String password = args[1].toString();
        signatories.put(name, createSignatory(name, secretKey, password));
    }

    private PgpSignatory createSignatory(String name, String secretKey, String password) {
        try (InputStream in = PGPUtil.getDecoderStream(new ByteArrayInputStream(secretKey.getBytes(UTF_8)))) {
            PGPSecretKey key = new JcaPGPSecretKeyRing(in).getSecretKey();
            return factory.createSignatory(name, key, password);
        } catch (Exception e) {
            throw new InvalidUserDataException("Could not read PGP secret key", e);
        }
    }

}
