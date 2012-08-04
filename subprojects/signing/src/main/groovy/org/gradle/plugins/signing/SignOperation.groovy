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
 * <p>
 * A sign operation manages one or more {@link Signature} objects. The {@code sign} methods are used to register things to generate signatures
 * for. The {@link #execute()} method generates the signatures for all of the registered items at that time.
 */
class SignOperation implements SignatureSpec {

    /**
     * The file representation of the signature(s).
     */
    SignatureType signatureType
    
    /**
     * The signatory to the generated digital signatures.
     */
    Signatory signatory

    /**
     * Whether or not it is required that this signature be generated.
     */
    boolean required

    final private List<Signature> signatures = []
        
    String getDisplayName() {
        "SignOperation"
    }
    
    String toString() {
        getDisplayName()
    }
    
    /**
     * Registers signatures for the given artifacts.
     * 
     * @see Signature#Signature(PublishArtifact,SignatureSpec,Object)
     * @return this
     */
    SignOperation sign(PublishArtifact... artifacts) {
        for (artifact in artifacts) {
            signatures << new Signature(artifact, this)
        }
        this
    }
    
    /**
     * Registers signatures for the given files.
     * 
     * @see Signature#Signature(File,SignatureSpec,Object)
     * @return this
     */
    SignOperation sign(File... files) {
        for (file in files) {
            signatures << new Signature(file, this)
        }
        this
    }
    
    /**
     * Registers signatures (with the given classifier) for the given files
     * 
     * @see Signature#Signature(File,String,SignatureSpec,Object)
     * @return this
     */
    SignOperation sign(String classifier, File... files) {
        for (file in files) {
            signatures << new Signature(file, classifier, this)
        }
        this
    }
    
    /**
     * Change the signature type for signature generation.
     */
    SignOperation signatureType(SignatureType type) {
        this.type = type
        this
    }
    
    /**
     * Change the signatory for signature generation.
     */
    SignOperation signatory(Signatory signatory) {
        this.signatory = signatory
    }
    
    /**
     * Executes the given closure against this object.
     */
    SignOperation configure(Closure closure) {
        ConfigureUtil.configure(closure, this, false)
        this
    }
    
    /**
     * Generates actual signature files for all of the registered signatures.
     * 
     * <p>The signatures are generated with the configuration they have at this time, which includes the signature type
     * and signatory of this operation at this time.
     * <p>
     * This method can be called multiple times, with the signatures being generated with their current configuration each time.
     * 
     * @see Signature#generate()
     * @return this
     */
    SignOperation execute() {
        signatures*.generate()
        this
    }
    
    /**
     * The registered signatures.
     */
    List<Signature> getSignatures() {
        new ArrayList(signatures)
    }
    
    /**
     * Returns the the single registered signature.
     *
     * @return The signature.
     * @throws IllegalStateException if there is not exactly one registered signature.
     */
    Signature getSingleSignature() {
        if (signatures.size() == 0) {
            throw new IllegalStateException("Expected %s to contain exactly one signature, however, it contains no signatures.")
        } else if (signatures.size() == 1) {
            signatures.first()
        } else {
            throw new IllegalStateException("Expected %s to contain exactly one signature, however, it contains no ${signature.size()} signatures.")
        }
    }
    
    /**
     * All of the files that will be signed by this operation.
     */
    FileCollection getFilesToSign() {
        new SimpleFileCollection(*signatures*.toSign.findAll({ it != null }))
    }
    
    /**
     * All of the signature files that will be generated by this operation.
     */
    FileCollection getSignatureFiles() {
        new SimpleFileCollection(*signatures*.file.findAll({ it != null }))
    }

}