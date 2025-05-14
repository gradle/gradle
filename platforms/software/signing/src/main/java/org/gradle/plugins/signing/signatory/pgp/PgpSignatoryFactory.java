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
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection;
import org.bouncycastle.openpgp.bc.BcPGPSecretKeyRingCollection;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.jspecify.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;

import static org.gradle.internal.Cast.uncheckedCast;
import static org.gradle.internal.IoActions.uncheckedClose;

/**
 * Creates {@link PgpSignatory} instances.
 */
public class PgpSignatoryFactory {

    private static final String[] PROPERTIES = new String[]{"keyId", "secretKeyRingFile", "password"};

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

    protected PgpSignatory readProperties(Project project, String prefix, String name) {
        return readProperties(project, prefix, name, false);
    }

    protected PgpSignatory readProperties(Project project, String prefix, String name, boolean required) {
        ArrayList<Object> values = new ArrayList<>();
        for (String property : PROPERTIES) {
            String qualifiedProperty = (String)getQualifiedPropertyName(prefix, property);
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
        return createSignatory(name, keyId, keyRing, password);
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

    public PGPSecretKey readSecretKey(final String keyId, final File file) {
        InputStream inputStream = openSecretKeyFile(file);
        try {
            return readSecretKey(inputStream, keyId, "file: " + file.getAbsolutePath());
        } finally {
            uncheckedClose(inputStream);
        }
    }

    private InputStream openSecretKeyFile(File file) {
        try {
            return new BufferedInputStream(new FileInputStream(file));
        } catch (FileNotFoundException e) {
            throw new InvalidUserDataException("Unable to retrieve secret key from key ring file '" + file + "' as it does not exist");
        }
    }

    protected PGPSecretKey readSecretKey(InputStream input, String keyId, String sourceDescription) {
        PGPSecretKeyRingCollection keyRingCollection;
        try {
            keyRingCollection = new BcPGPSecretKeyRingCollection(input);
        } catch (Exception e) {
            throw new InvalidUserDataException("Unable to read secret key from " + sourceDescription + " (it may not be a PGP secret key ring)", e);
        }
        return readSecretKey(keyRingCollection, normalizeKeyId(keyId), sourceDescription);
    }

    protected PGPSecretKey readSecretKey(PGPSecretKeyRingCollection keyRings, final PgpKeyId keyId, String sourceDescription) {
        PGPSecretKey key = findSecretKey(keyRings, keyId);
        if (key == null) {
            throw new InvalidUserDataException("did not find secret key for id '" + keyId + "' in key source '" + sourceDescription + "'");
        }
        return key;
    }

    @Nullable
    private PGPSecretKey findSecretKey(PGPSecretKeyRingCollection keyRings, PgpKeyId keyId) {
        Iterator<PGPSecretKeyRing> keyRingIterator = uncheckedCast(keyRings.getKeyRings());
        while (keyRingIterator.hasNext()) {
            PGPSecretKeyRing keyRing = keyRingIterator.next();
            Iterator<PGPSecretKey> secretKeyIterator = uncheckedCast(keyRing.getSecretKeys());
            while (secretKeyIterator.hasNext()) {
                PGPSecretKey secretKey = secretKeyIterator.next();
                if (hasId(keyId, secretKey)) {
                    return secretKey;
                }
            }
        }
        return null;
    }

    private boolean hasId(PgpKeyId keyId, PGPSecretKey secretKey) {
        return new PgpKeyId(secretKey.getKeyID()).equals(keyId);
    }

    protected PgpKeyId normalizeKeyId(String keyId) {
        try {
            return new PgpKeyId(keyId);
        } catch (IllegalArgumentException e) {
            throw new InvalidUserDataException(e.getMessage());
        }
    }
}
