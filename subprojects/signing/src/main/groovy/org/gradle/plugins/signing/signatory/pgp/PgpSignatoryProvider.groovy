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
package org.gradle.plugins.signing.signatory.pgp

import org.gradle.plugins.signing.signatory.SignatoryProvider

import org.gradle.util.ConfigureUtil

import org.gradle.api.Project

import org.gradle.plugins.signing.SigningExtension

class PgpSignatoryProvider implements SignatoryProvider<PgpSignatory> {
    
    private final factory = new PgpSignatoryFactory()
    private final Map<String, PgpSignatory> signatories = [:]
    
    void configure(SigningExtension settings, Closure closure) {
        ConfigureUtil.configure(closure, new Dsl(settings.project, signatories, factory))
    }
    
    PgpSignatory getDefaultSignatory(Project project) {
        factory.createSignatory(project)
    }
    
    PgpSignatory getSignatory(String name) {
        signatories[name]
    }
    
    PgpSignatory propertyMissing(String signatoryName) {
        getSignatory(signatoryName)
    }
}

class Dsl {
    
    private final project
    private final signatories
    private final factory
    
    Dsl(Project project, Map<String, PgpSignatory> signatories, PgpSignatoryFactory factory) {
        this.project = project
        this.signatories = signatories
        this.factory = factory
    }
    
    def methodMissing(String name, args) {
        def signatory
        if (args.size() == 3) {
            def keyId = args[0].toString()
            def keyRing = project.file(args[1].toString())
            def password = args[2].toString()

            signatory = factory.createSignatory(name, keyId, keyRing, password)
        } else if (args.size() == 0) {
            signatory = factory.createSignatory(project, name, true)
        } else {
            throw new Exception("Invalid args ($name: $args)")
        }
        
        signatories[signatory.name] = signatory
    }
}
