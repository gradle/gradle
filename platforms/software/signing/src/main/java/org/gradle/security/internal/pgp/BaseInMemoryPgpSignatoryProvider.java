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

import org.gradle.api.Project;
import org.gradle.plugins.signing.signatory.pgp.PgpSignatory;
import org.gradle.plugins.signing.signatory.pgp.PgpSignatoryFactory;
import org.gradle.security.internal.BaseSignatoryProvider;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A {@link BaseSignatoryProvider} of {@link PgpSignatory} instances read from
 * ascii-armored in-memory secret keys instead of a keyring.
 */
public class BaseInMemoryPgpSignatoryProvider implements BaseSignatoryProvider<PgpSignatory> {

    private final PgpSignatoryFactory factory = new PgpSignatoryFactory();
    private final Map<String, PgpSignatory> signatories = new LinkedHashMap<>();
    private final @Nullable PgpSignatory defaultSignatory;

    public BaseInMemoryPgpSignatoryProvider(Project project, @Nullable String defaultKeyId, @Nullable String defaultSecretKey, @Nullable String defaultPassword) {
        this.defaultSignatory = defaultSecretKey != null && defaultPassword != null
            ? createSignatory(project, "default", defaultKeyId, defaultSecretKey, defaultPassword)
            : null;
    }

    @Override
    public @Nullable PgpSignatory getDefaultSignatory(Project project) {
        return defaultSignatory;
    }

    @Override
    public PgpSignatory getSignatory(String name) {
        return signatories.get(name);
    }

    protected void addSignatory(Project project, String name, String keyId, String secretKey, String password) {
        signatories.put(name, createSignatory(project, name, keyId, secretKey, password));
    }

    private PgpSignatory createSignatory(Project project, String name, @Nullable String keyId, String secretKey, String password) {
        return factory.createSignatory(project, name, keyId, secretKey, password);
    }

}
