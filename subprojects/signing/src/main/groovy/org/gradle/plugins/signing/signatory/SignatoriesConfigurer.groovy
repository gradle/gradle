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
package org.gradle.plugins.signing.signatory

import org.gradle.plugins.signing.SigningSettings

class SignatoriesConfigurer {
    
    private final SigningSettings config
    private final SignatoryFactory factory
    
    SignatoriesConfigurer(SigningSettings config) {
        this.config = config
        this.factory = new SignatoryFactory()
    }
    
    def methodMissing(String name, args) {
        def signatories = config.signatories
        def project = config.project
        
        if (args.size() == 3) {
            def keyId = args[0].toString()
            def keyRing = project.file(args[1].toString())
            def password = args[2].toString()
            
            signatories[name] = factory.createSignatory(keyId, keyRing, password)
        } else if (args.size() == 0) {
            signatories[name] = factory.createSignatory(project, name, true)
        } else {
            throw new Exception("Invalid args ($name: $args)")
        }
    }
}
