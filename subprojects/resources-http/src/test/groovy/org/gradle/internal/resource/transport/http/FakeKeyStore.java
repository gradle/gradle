/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.resource.transport.http;

import org.apache.commons.lang.NotImplementedException;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.security.Key;
import java.security.KeyStoreException;
import java.security.KeyStoreSpi;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

public class FakeKeyStore extends KeyStoreSpi {
    private Map<String, Key> keys = new HashMap<>();

    private Map<String, Certificate[]> certChains = new HashMap<>();

    @Override
    public Key engineGetKey(String alias, char[] password) throws NoSuchAlgorithmException, UnrecoverableKeyException {
        return keys.get(alias);
    }

    @Override
    public Certificate[] engineGetCertificateChain(String alias) {
        return certChains.get(alias);
    }

    @Override
    public Certificate engineGetCertificate(String alias) {
        Certificate[] certs = certChains.get(alias);
        if (certs != null) {
            return certs[0];
        } else {
            return null;
        }
    }

    @Override
    public Date engineGetCreationDate(String alias) {
        return new Date();
    }

    @Override
    public void engineSetKeyEntry(String alias, Key key, char[] password, Certificate[] chain) throws KeyStoreException {
        keys.put(alias, key);
        certChains.put(alias, chain);
    }

    @Override
    public void engineSetKeyEntry(String alias, byte[] key, Certificate[] chain) throws KeyStoreException {
        throw new NotImplementedException();
    }

    @Override
    public void engineSetCertificateEntry(String alias, Certificate cert) throws KeyStoreException {
        Certificate[] certs = certChains.get(alias);
        if (certs == null) {
            Certificate[] newCerts = new Certificate[1];
            newCerts[0] = cert;
            certChains.put(alias, newCerts);
        } else {
            Certificate[] updatedCerts = Arrays.copyOf(certs, certs.length + 1);
            updatedCerts[certs.length] = cert;
            certChains.put(alias, updatedCerts);
        }
    }

    @Override
    public void engineDeleteEntry(String alias) throws KeyStoreException {
        keys.remove(alias);
        certChains.remove(alias);
    }

    @Override
    public Enumeration<String> engineAliases() {
        return Collections.enumeration(keys.keySet());
    }

    @Override
    public boolean engineContainsAlias(String alias) {
        return keys.containsKey(alias) || certChains.containsKey(alias);
    }

    @Override
    public int engineSize() {
        return keys.size() + certChains.size();
    }

    @Override
    public boolean engineIsKeyEntry(String alias) {
        return keys.containsKey(alias);
    }

    @Override
    public boolean engineIsCertificateEntry(String alias) {
        return certChains.containsKey(alias);
    }

    @Override
    public String engineGetCertificateAlias(Certificate cert) {
        throw new NotImplementedException();
    }

    @Override
    public void engineStore(OutputStream stream, char[] password) throws IOException, NoSuchAlgorithmException, CertificateException {
        ObjectOutputStream oos = new ObjectOutputStream(stream);
        oos.writeObject(keys);
        oos.writeObject(certChains);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void engineLoad(InputStream stream, char[] password) throws IOException, NoSuchAlgorithmException, CertificateException {
        if (stream == null) {
            return;
        }
        ObjectInputStream ois = new ObjectInputStream(stream);
        try {
            keys = (Map<String, Key>) ois.readObject();
            certChains = (Map<String, Certificate[]>) ois.readObject();
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    static class Provider extends java.security.Provider {
        public static String name = "FakeProvider";
        public static String algorithm = "FAKEKS";

        Provider() {
            super(name, 1.0, name);
            putService(new Service(this, "KeyStore", algorithm, FakeKeyStore.class.getCanonicalName(), null, null));
        }
    }
}
