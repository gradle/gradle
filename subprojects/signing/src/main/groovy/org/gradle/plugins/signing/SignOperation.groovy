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
package org.gradle.plugins.signing

import org.gradle.api.artifacts.PublishArtifact

import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.collections.SimpleFileCollection

import org.gradle.util.ConfigureUtil

import org.gradle.plugins.signing.signatory.Signatory
import org.gradle.plugins.signing.type.SignatureType

/**
 * A sign operation creates digital signatures for one or more files or {@link org.gradle.api.artifacts.PublishArtifact publish artifacts}.
 * 
 * <p>The external representation of the signature is specified by the {@link #type signature type property}, while the {@link #signatory}
 * property specifies who is to sign.
 */
class SignOperation implements SignatureSpec {

    SignatureType signatureType
    Signatory signatory
    
    final private List<Signature> signatures = []
        
    String getDisplayName() {
        "SignOperation"
    }
    
    String toString() {
        getDisplayName()
    }
    
    SignOperation sign(PublishArtifact... artifacts) {
        for (artifact in artifacts) {
            signatures << new Signature(artifact, this)
        }
        this
    }
    
    SignOperation sign(File... files) {
        for (file in files) {
            signatures << new Signature(file, this)
        }
        this
    }
    
    SignOperation sign(String classifier, File... files) {
        for (file in files) {
            signatures << new Signature(file, classifier, this)
        }
        this
    }

    SignOperation signatureType(SignatureType type) {
        this.type = type
        this
    }
    
    SignOperation signatory(Signatory signatory) {
        this.signatory = signatory
    }

    SignOperation configure(Closure closure) {
        ConfigureUtil.configure(closure, this)
        this
    }
    
    SignOperation execute() {
        signatures*.generate()
        this
    }
    
    List<Signature> getSignatures() {
        new ArrayList(signatures)
    }
    
    Signature getSingleSignature() {
        if (signatures.size() == 0) {
            throw new IllegalStateException("Expected %s to contain exactly one signature, however, it contains no signatures.")
        } else if (signatures.size() == 1) {
            signatures.first()
        } else {
            throw new IllegalStateException("Expected %s to contain exactly one signature, however, it contains no ${signature.size()} signatures.")
        }
    }
    
    FileCollection getFilesToSign() {
        new SimpleFileCollection(*signatures*.toSign.findAll({ it != null }))
    }
    
    FileCollection getSignatureFiles() {
        new SimpleFileCollection(*signatures*.file.findAll({ it != null }))
    }

}