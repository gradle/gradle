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
package org.gradle.plugins.signing.signatory.pgp;

import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection;
import org.gradle.api.Incubating;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.internal.lambdas.SerializableLambdas.SerializableTransformer;
import org.gradle.api.internal.provider.Providers;
import org.gradle.api.provider.Provider;
import org.gradle.plugins.signing.SigningExtension;
import org.gradle.plugins.signing.signatory.internal.pgp.KeyDataInternService;
import org.gradle.plugins.signing.signatory.internal.pgp.PgpSignatoryService;
import org.gradle.plugins.signing.signatory.internal.pgp.PgpSignatoryUtil;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;

import static org.gradle.api.internal.lambdas.SerializableLambdas.transformer;

/**
 * Creates {@link PgpSignatory} instances.
 */
public class PgpSignatoryFactory {

    private static final String[] PROPERTIES = new String[]{"keyId", "secretKeyRingFile", "password"};

    // Most of these methods are here because they once leaked into Public API.

    public PgpSignatory createSignatory(Project project, boolean required) {
        return readProperties(project, null, "default", required);
    }

    public PgpSignatory createSignatory(Project project) {
        return createSignatory(project, false);
    }

    public PgpSignatory createSignatory(Project project, String propertyPrefix, boolean required) {
        return readProperties(project, propertyPrefix, propertyPrefix, required);
    }

    public PgpSignatory createSignatory(Project project, String propertyPrefix) {
        return createSignatory(project, propertyPrefix, false);
    }

    public PgpSignatory createSignatory(Project project, String propertyPrefix, String name, boolean required) {
        return readProperties(project, propertyPrefix, name, required);
    }

    public PgpSignatory createSignatory(Project project, String propertyPrefix, String name) {
        return createSignatory(project, propertyPrefix, name, false);
    }

    public PgpSignatory createSignatory(String name, String keyId, File keyRing, String password) {
        return createSignatory(name, readSecretKey(keyId, keyRing), password);
    }

    public PgpSignatory createSignatory(String name, PGPSecretKey secretKey, String password) {
        return new PgpSignatory(name, secretKey, password);
    }

    /**
     * Creates a {@link PgpSignatory} named {@code name} based on the private key with id {@code keyId} stored in the key ring file {@code keyRing} with {@code password} password.
     * <p>
     * In most cases, you want to use {@link SigningExtension} to create signatories instead of this method.
     *
     * @param project the project to which the signing plugin has been applied
     * @param name the name for the signatory
     * @param keyId the key id to look it up in the keyring
     * @param keyRing the keyring file
     * @param password the password for the private key
     * @return the signatory instance
     *
     * @since 9.5.0
     */
    @Incubating
    public PgpSignatory createSignatory(Project project, String name, String keyId, File keyRing, String password) {
        return createLazySignatory(project, name, keyId, keyRing, password);
    }

    /**
     * Creates a {@link PgpSignatory} named {@code name} based on the private key with id {@code keyId} stored in the ASCII-armored {@code key} with {@code password} password.
     * <p>
     * In most cases, you want to use {@link SigningExtension} to create signatories instead of this method.
     *
     * @param project the project to which the signing plugin has been applied
     * @param name the name for the signatory
     * @param keyId the key id to look it up in the keyring (optional if keyring contains only one master key, and it should be used)
     * @param key the ASCII-armored key
     * @param password the password for the private key
     * @return the signatory instance
     *
     * @since 9.5.0
     */
    @Incubating
    public PgpSignatory createSignatory(Project project, String name, @Nullable String keyId, String key, String password) {
        return createLazySignatory(project, name, keyId, key, password);
    }

    protected PgpSignatory readProperties(Project project, String prefix, String name) {
        return readProperties(project, prefix, name, false);
    }

    public PGPSecretKey readSecretKey(final String keyId, final File file) {
        return PgpSignatoryUtil.readSecretKey(keyId, file);
    }

    protected PGPSecretKey readSecretKey(InputStream input, String keyId, String sourceDescription) {
        return PgpSignatoryUtil.readSecretKey(input, keyId, sourceDescription);
    }

    protected PGPSecretKey readSecretKey(PGPSecretKeyRingCollection keyRings, final PgpKeyId keyId, String sourceDescription) {
        return PgpSignatoryUtil.readSecretKey(keyRings, keyId, sourceDescription);
    }

    protected PgpKeyId normalizeKeyId(String keyId) {
        return PgpSignatoryUtil.normalizeKeyId(keyId);
    }

    private PgpSignatory createLazySignatory(Project project, String name, String keyId, File keyRing, String password) {
        // The returned signatory instance doesn't track changes to originating properties (and never did).
        // In order to account for property mutability, the calling code recreates signatories over and over.
        return createLazySignatory(project, name, service -> service.readSecretKeyFromKeyRingFile(keyId, keyRing, password));
    }

    private PgpSignatory createLazySignatory(Project project, String name, String keyId, String keyData, String password) {
        // keyData may be several KB long, and each project may try to register one, though with the same contents.
        // Other strings aren't as long/critical and interning them will likely hurt performance more than help.
        String internedKeyData = KeyDataInternService.obtain(project).get().internKeyData(keyData);
        return createLazySignatory(project, name, service -> service.readSecretKeyFromArmoredString(keyId, internedKeyData, password));
    }

    private PgpSignatory createLazySignatory(Project project, String name, SerializableTransformer<PgpSignatoryService.ParsedPgpKey, PgpSignatoryService> keyBuilder) {
        Provider<PgpSignatoryService> signatoryService = PgpSignatoryService.obtain(project);
        // Trying to read the key has a cost.
        Provider<PgpSignatoryService.ParsedPgpKey> keyData = Providers.memoizing(Providers.internal(signatoryService.map(keyBuilder)));

        return new PgpSignatoryInternal(
            signatoryService,
            name,
            keyData.map(transformer(PgpSignatoryService.ParsedPgpKey::getSecretKey)),
            keyData.map(transformer(PgpSignatoryService.ParsedPgpKey::getPrivateKey))
        );
    }

    protected PgpSignatory readProperties(Project project, String prefix, String name, boolean required) {
        ArrayList<Object> values = new ArrayList<>();
        for (String property : PROPERTIES) {
            String qualifiedProperty = (String) getQualifiedPropertyName(prefix, property);
            Object prop = getPropertySafely(project, qualifiedProperty, required);
            if (prop != null) {
                values.add(prop);
            } else {
                return null;
            }
        }

        String keyId = values.get(0).toString();
        File keyRing = project.file(values.get(1).toString());
        String password = values.get(2).toString();
        return createLazySignatory(project, name, keyId, keyRing, password);
    }

    private Object getPropertySafely(Project project, String qualifiedProperty, boolean required) {
        final boolean propertyFound = project.hasProperty(qualifiedProperty);

        if (!propertyFound && required) {
            throw new InvalidUserDataException("property '" + qualifiedProperty + "' could not be found on project and is needed for signing");
        }

        if (propertyFound) {
            Object prop = project.property(qualifiedProperty);
            if (prop == null && required) {
                throw new InvalidUserDataException("property '" + qualifiedProperty + "' was null. A valid value is needed for signing");
            }
            return prop;
        }

        return null;
    }

    protected Object getQualifiedPropertyName(final String propertyPrefix, final String name) {
        return "signing." + (propertyPrefix != null ? propertyPrefix + "." : "") + name;
    }
}
