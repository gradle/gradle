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

import org.gradle.api.Project;
import org.gradle.plugins.signing.signatory.internal.ConfigurableSignatoryProvider;
import org.gradle.security.internal.pgp.BaseInMemoryPgpSignatoryProvider;
import org.gradle.plugins.signing.signatory.pgp.PgpSignatory;

import java.util.Arrays;

public class InMemoryPgpSignatoryProvider extends BaseInMemoryPgpSignatoryProvider implements ConfigurableSignatoryProvider<PgpSignatory> {
    public InMemoryPgpSignatoryProvider(String defaultSecretKey, String defaultPassword) {
        super(defaultSecretKey, defaultPassword);
    }

    public InMemoryPgpSignatoryProvider(String defaultKeyId, String defaultSecretKey, String defaultPassword) {
        super(defaultKeyId, defaultSecretKey, defaultPassword);
    }

    @Override
    public void createSignatoryFor(Project project, String name, Object[] args) {
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
        addSignatory(name, keyId, secretKey, password);
    }

    @SuppressWarnings("unused") // invoked by Groovy
    public PgpSignatory propertyMissing(String signatoryName) {
        return getSignatory(signatoryName);
    }

}
