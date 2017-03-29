/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.plugins.signing.signatory.gnupg;

import org.gradle.api.Project;

import java.util.Map;

import static org.codehaus.groovy.runtime.DefaultGroovyMethods.asType;

/**
 * Configuration for {@link GnupgSignatoryProvider}.
 */
class Dsl {

    private final Project project;
    private final Map<String, GnupgSignatory> signatories;
    private final GnupgSignatoryFactory factory;

    Dsl(Project project, Map<String, GnupgSignatory> signatories, GnupgSignatoryFactory factory) {
        this.project = project;
        this.signatories = signatories;
        this.factory = factory;
    }

    @SuppressWarnings("unused") // invoked by Groovy
    public GnupgSignatory methodMissing(String name, Object args) {
        GnupgSignatory signatory = createSignatoryFor(name, asType(args, Object[].class));
        signatories.put(signatory.getName(), signatory);
        return signatory;
    }

    private GnupgSignatory createSignatoryFor(String name, Object[] args) {
        if (args.length != 0) {
            throw new IllegalArgumentException("Invalid args (" + name + ": " + String.valueOf(args) + ")");
        }
        return factory.createSignatory(project, name, name);
    }

}
