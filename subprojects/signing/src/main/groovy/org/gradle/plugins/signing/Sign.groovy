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

import org.gradle.api.Task
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.file.FileCollection
import org.gradle.api.InvalidUserDataException

import org.gradle.plugins.signing.signatory.Signatory
import org.gradle.plugins.signing.type.SignatureType

/**
 * Creates a file containing a digital signature alongside an input file.
 */
class Sign extends DefaultTask {
    
    final SigningSettings settings
    final protected SignOperation operation
    
    private Boolean required
    
    Sign() {
        super()
        settings = project.signing
        operation = new SignOperation(settings)

        // If we aren't required and don't have a signatory then we just don't run
        onlyIf {
            getRequired() || getSignatory() != null
        }

        // Have to include this in the up-to-date checks because the signatory may have changed
        inputs.property("signatory") { getSignatory()?.keyId?.asHex }
    }
    
    void sign(Task... tasks) {
        for (it in tasks) {
            if (!(it instanceof AbstractArchiveTask)) {
                throw new InvalidUserDataException("You cannot sign tasks that are not 'archive' tasks, such as 'jar', 'zip' etc. (you tried to sign $it)")
            }
            
            dependsOn(it)
            addSignature(it, it.archivePath, it.classifier)
        }
    }
    
    void sign(PublishArtifact... toSign) {
        for (it in toSign) {
            dependsOn(it.buildDependencies)
            addSignature(it, it.file, it.classifier)
        }
    }
    
    void sign(File... toSign) {
        sign(null, *toSign)
    }
    
    void sign(String classifier, File... toSign) {
        for (it in toSign) {
            addSignature(it, it, classifier)
        }
    }
    
    void sign(Configuration... configurations) {
        for (it in configurations) {
            dependsOn(it.buildArtifacts)
            sign(*it.allArtifacts.toList())
        }
    }
    
    private addSignature(Object source, File toSign, String classifier = null) {
        def signature = operation.addSignature(source, toSign, classifier, this)
        inputs.file(toSign)
        outputs.file(signature.file)
    }
    
    void signatory(Signatory signatory) {
        operation.signatory(signatory)
    }
        
    Signatory getSignatory() {
        operation.signatory
    }

    void type(SignatureType type) {
        operation.type(type)
    }

    SignatureType getType() {
        operation.type
    }
    
    boolean getRequired() {
        required != null ? required : settings.required
    }
    
    void setRequired(boolean required) {
        this.required = required
    }
    
    void required(boolean required) {
        setRequired(required)
    }
    
    FileCollection getSigned() {
        operation.signed
    }
    
    FileCollection getFiles() {
        operation.files
    }

    PublishArtifact[] getArtifacts() {
        operation.artifacts
    }

    PublishArtifact getSingleArtifact() {
        operation.singleArtifact
    }
    
    Signature getSingleSignature() {
        operation.singleSignature
    }
    
    @TaskAction
    void signIt() {
        if (getSignatory() == null) {
            throw new InvalidUserDataException("Cannot perform signing task '${getPath()}' because it has no configured signatory")
        }
        operation.execute()
    }
}