/*
 * Copyright 2011 the original author or authors.
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

import java.io.File;
import java.util.Map;

import static org.codehaus.groovy.runtime.DefaultGroovyMethods.asType;

/**
 * Configuration dsl for {@link PgpSignatoryProvider}.
 */
class Dsl {

    private final Project project;
    private final Map<String, PgpSignatory> signatories;
    private final PgpSignatoryFactory factory;

    public Dsl(Project project, Map<String, PgpSignatory> signatories, PgpSignatoryFactory factory) {
        this.project = project;
        this.signatories = signatories;
        this.factory = factory;
    }

    @SuppressWarnings("unused") // invoked by Groovy
    public PgpSignatory methodMissing(String name, Object args) {
        PgpSignatory signatory = signatoryFor(name, asType(args, Object[].class));
        signatories.put(signatory.getName(), signatory);
        return signatory;
    }

    private PgpSignatory signatoryFor(String name, Object[] args) {
        switch (args.length) {
            case 3:
                String keyId = args[0].toString();
                File keyRing = project.file(args[1].toString());
                String password = args[2].toString();
                return factory.createSignatory(name, keyId, keyRing, password);
            case 0:
                return factory.createSignatory(project, name, true);
            default:
                throw new IllegalArgumentException("Invalid args (" + name + ": " + String.valueOf(args) + ")");
        }
    }
}
