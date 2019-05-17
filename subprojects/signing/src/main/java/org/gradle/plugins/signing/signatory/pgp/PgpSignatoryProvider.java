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

import groovy.lang.Closure;
import org.gradle.api.Project;
import org.gradle.plugins.signing.SigningExtension;
import org.gradle.plugins.signing.signatory.SignatoryProvider;
import org.gradle.util.ConfigureUtil;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.codehaus.groovy.runtime.DefaultGroovyMethods.asType;

/**
 * A {@link SignatoryProvider} of {@link PgpSignatory} instances.
 */
public class PgpSignatoryProvider implements SignatoryProvider<PgpSignatory> {

    private final PgpSignatoryFactory factory = new PgpSignatoryFactory();

    private final Map<String, PgpSignatory> signatories = new LinkedHashMap<String, PgpSignatory>();

    @Override
    public void configure(final SigningExtension settings, Closure closure) {
        ConfigureUtil.configure(closure, new Object() {
            @SuppressWarnings("unused") // invoked by Groovy
            public void methodMissing(String name, Object args) {
                createSignatoryFor(settings.getProject(), name, asType(args, Object[].class));
            }
        });
    }

    private void createSignatoryFor(Project project, String name, Object[] args) {
        switch (args.length) {
            case 3:
                String keyId = args[0].toString();
                File keyRing = project.file(args[1].toString());
                String password = args[2].toString();
                signatories.put(name, factory.createSignatory(name, keyId, keyRing, password));
                break;
            case 0:
                signatories.put(name, factory.createSignatory(project, name, true));
                break;
            default:
                throw new IllegalArgumentException("Invalid args (" + name + ": " + String.valueOf(args) + ")");
        }
    }

    @Override
    public PgpSignatory getDefaultSignatory(Project project) {
        return factory.createSignatory(project);
    }

    @Override
    public PgpSignatory getSignatory(String name) {
        return signatories.get(name);
    }

    @SuppressWarnings("unused") // invoked by Groovy
    public PgpSignatory propertyMissing(String signatoryName) {
        return getSignatory(signatoryName);
    }
}
