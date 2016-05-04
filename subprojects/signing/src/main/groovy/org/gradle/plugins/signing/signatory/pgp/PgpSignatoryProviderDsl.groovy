/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.plugins.signing.signatory.pgp

import groovy.transform.CompileStatic
import org.gradle.api.Project

@CompileStatic
class PgpSignatoryProviderDsl {

    private final Project project
    private final Map<String, PgpSignatory> signatories
    private final PgpSignatoryFactory factory

    PgpSignatoryProviderDsl(Project project, Map<String, PgpSignatory> signatories, PgpSignatoryFactory factory) {
        this.project = project
        this.signatories = signatories
        this.factory = factory
    }

    def methodMissing(String name, Object argList) {
        PgpSignatory signatory = signatoryFor(name, argList as Object[])
        signatories[signatory.name] = signatory
    }

    private PgpSignatory signatoryFor(String name, Object[] args) {
        if (args.size() == 3) {
            def keyId = args[0].toString()
            def keyRing = project.file(args[1].toString())
            def password = args[2].toString()

            factory.createSignatory(name, keyId, keyRing, password)
        } else if (args.size() == 0) {
            factory.createSignatory(project, name, true)
        } else {
            throw new Exception("Invalid args ($name: $args)")
        }
    }
}
