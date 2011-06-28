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
import org.gradle.api.internal.ClassGenerator

import org.gradle.plugins.signing.signatory.Signatory
import org.gradle.plugins.signing.type.SignatureType

/**
 * <p>A task for signing one or more; tasks, files or configurations.</p>
 * 
 * <p></p>
 */
class Sign extends DefaultTask implements SignatureSpec {
    
    SignatureType type
    
    /**
     * The signatory
     */
    Signatory signatory
    
    final protected SignOperation operation
    
    boolean required = true
    
    Sign() {
        super()
        operation = project.services.get(ClassGenerator).newInstance(SignOperation)
        operation.conventionMapping.map("type") { this.getType() }
        operation.conventionMapping.map("signatory") { this.getSignatory() }

        // If we aren't required and don't have a signatory then we just don't run
        onlyIf {
            isRequired() || getSignatory() != null
        }

        // Have to include this in the up-to-date checks because the signatory may have changed
        inputs.property("signatory") { getSignatory()?.keyId?.asHex }
    }
    
    /**
     * Configures the task to sign the archive produced for each of the given tasks (which must be archive tasks)
     */
    void sign(Task... tasks) {
        for (it in tasks) {
            if (!(it instanceof AbstractArchiveTask)) {
                throw new InvalidUserDataException("You cannot sign tasks that are not 'archive' tasks, such as 'jar', 'zip' etc. (you tried to sign $it)")
            }
            
            dependsOn(it)
            addSignature(it, it.archivePath, it.classifier)
        }
    }

    /**
     * Configures the task to sign each of the given artifacts
     */
    void sign(PublishArtifact... publishArtifacts) {
        for (it in publishArtifacts) {
            dependsOn(it.buildDependencies)
            addSignature(it, it.file, it.classifier)
        }
    }

    /**
     * Configures the task to sign each of the given files
     */    
    void sign(File... files) {
        sign(null, *files)
    }
    
    /**
     * Configures the task to sign each of the given artifacts, using the given classifier as the classifier for the resultant signature publish artifact.
     */
    void sign(String classifier, File... files) {
        for (it in files) {
            addSignature(it, it, classifier)
        }
    }

    /**
     * Configures the task to sign every artifact of the given configurations
     */
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
    
    void type(SignatureType type) {
        this.type = type
    }
    
    /**
     * {@inheritDoc}
     */
    void signatory(Signatory signatory) {
        this.signatory = signatory
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