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

import org.gradle.api.Project;
import org.gradle.plugins.signing.signatory.SignatoryProvider;
import org.gradle.plugins.signing.signatory.internal.ConfigurableSignatoryProvider;
import org.gradle.security.internal.pgp.BasePgpSignatoryProvider;

import java.io.File;
import java.util.Arrays;

/**
 * A {@link SignatoryProvider} of {@link PgpSignatory} instances.
 */
public class PgpSignatoryProvider extends BasePgpSignatoryProvider implements ConfigurableSignatoryProvider<PgpSignatory> {

    @Override
    public void createSignatoryFor(Project project, String name, Object[] args) {
        switch (args.length) {
            case 3:
                String keyId = args[0].toString();
                File keyRing = project.file(args[1].toString());
                String password = args[2].toString();
                createSignatory(name, keyId, keyRing, password);
                break;
            case 0:
                createSignatory(project, name, true);
                break;
            default:
                throw new IllegalArgumentException("Invalid args (" + name + ": " + Arrays.toString(args) + ")");
        }
    }

    @SuppressWarnings("unused") // invoked by Groovy
    public PgpSignatory propertyMissing(String signatoryName) {
        return getSignatory(signatoryName);
    }
}
