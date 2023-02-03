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
import org.gradle.security.internal.BaseSignatoryProvider;
import org.gradle.plugins.signing.signatory.pgp.PgpSignatory;
import org.gradle.plugins.signing.signatory.pgp.PgpSignatoryFactory;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A {@link BaseSignatoryProvider} of {@link PgpSignatory} instances.
 */
public class BasePgpSignatoryProvider implements BaseSignatoryProvider<PgpSignatory> {

    private final PgpSignatoryFactory factory = new PgpSignatoryFactory();

    private final Map<String, PgpSignatory> signatories = new LinkedHashMap<String, PgpSignatory>();

    @Override
    public PgpSignatory getDefaultSignatory(Project project) {
        return factory.createSignatory(project);
    }

    @Override
    public PgpSignatory getSignatory(String name) {
        return signatories.get(name);
    }

    public PgpSignatory createSignatory(String name, String keyId, File keyRing, String password) {
        return signatories.put(name, factory.createSignatory(name, keyId, keyRing, password));
    }

    public PgpSignatory createSignatory(Project project, String name, boolean required) {
        return signatories.put(name, factory.createSignatory(project, name, required));
    }
}
