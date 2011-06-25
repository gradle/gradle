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

import org.gradle.plugins.signing.SigningSettings
import org.gradle.plugins.signing.signatory.Signatory
import org.gradle.plugins.signing.signatory.SignatoryProvider

import org.gradle.util.ConfigureUtil

import org.gradle.api.Project

class PgpSignatoryProvider implements SignatoryProvider {
    
    private final factory = new PgpSignatoryFactory()
    
    void configure(SigningSettings settings, Closure closure) {
        ConfigureUtil.configure(closure, new Dsl(settings, factory))
    }
    
    Signatory getDefaultSignatory(Project project) {
        factory.createSignatory(project)
    }
    
}

class Dsl {
    
    private final settings
    private final factory
    
    Dsl(SigningSettings settings, PgpSignatoryFactory factory) {
        this.settings = settings
        this.factory = factory
    }
    
    def methodMissing(String name, args) {
        def project = settings.project

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
        
        settings.addSignatory(signatory)
    }
}
