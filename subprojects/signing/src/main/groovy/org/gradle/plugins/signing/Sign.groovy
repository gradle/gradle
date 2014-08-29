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
import org.gradle.api.DomainObjectSet
import org.gradle.api.tasks.TaskAction
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.InvalidUserDataException

import org.gradle.api.file.FileCollection
import org.gradle.api.internal.file.collections.SimpleFileCollection
import org.gradle.api.internal.DefaultDomainObjectSet

import org.gradle.plugins.signing.signatory.Signatory
import org.gradle.plugins.signing.type.SignatureType


/**
 * A task for creating digital signature files for one or more; tasks, files, publishable artifacts or configurations.
 * 
 * <p>The task produces {@link Signature}</p> objects that are publishable artifacts and can be assigned to another configuration.
 * <p>
 * The signature objects are created with defaults and using this tasks signatory and signature type.
 */
class Sign extends DefaultTask implements SignatureSpec {
    
    SignatureType signatureType
    
    /**
     * The signatory to the generated signatures.
     */
    Signatory signatory
    
    /**
     * Whether or not this task should fail if no signatory or signature type are configured at generation time.
     * 
     * <p>Defaults to {@code true}.</p>
     */
    boolean required = true
    
    final private DefaultDomainObjectSet<Signature> signatures = new DefaultDomainObjectSet<Signature>(Signature)
    
    Sign() {
        super()

        // If we aren't required and don't have a signatory then we just don't run
        onlyIf {
            isRequired() || getSignatory() != null
        }

        // Have to include this in the up-to-date checks because the signatory may have changed
        inputs.property("signatory") { getSignatory()?.keyId?.asHex }
        
        inputs.files { getSignatures()*.toSign }
        outputs.files { getSignatures()*.toSign }
    }
    
    /**
     * Configures the task to sign the archive produced for each of the given tasks (which must be archive tasks).
     */
    void sign(Task... tasks) {
        for (task in tasks) {
            if (!(task instanceof AbstractArchiveTask)) {
                throw new InvalidUserDataException("You cannot sign tasks that are not 'archive' tasks, such as 'jar', 'zip' etc. (you tried to sign $task)")
            }
            
            dependsOn(task)
            addSignature(new Signature({ task.archivePath }, { task.classifier }, this, this))
        }
    }

    /**
     * Configures the task to sign each of the given artifacts
     */
    void sign(PublishArtifact... publishArtifacts) {
        for (publishArtifact in publishArtifacts) {
            dependsOn(publishArtifact)
            addSignature(new Signature(publishArtifact, this, this))
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
        for (file in files) {
            addSignature(new Signature(file, classifier, this, this))
        }
    }

    /**
     * Configures the task to sign every artifact of the given configurations
     */
    void sign(Configuration... configurations) {
        for (configuration in configurations) {
            configuration.allArtifacts.all { PublishArtifact artifact ->
                if (!(artifact instanceof Signature)) {
                    sign(artifact)
                }
            }
            configuration.allArtifacts.whenObjectRemoved { PublishArtifact artifact ->
                signatures.remove(signatures.find { it.toSignArtifact == artifact })
            }
        }
    }
    
    private addSignature(Signature signature) {
        signatures.add(signature)
    }
    
    /**
     * Changes the signature file representation for the signatures.
     */
    void signatureType(SignatureType type) {
        this.signatureType = signatureType
    }
    
    /**
     * Changes the signatory of the signatures.
     */
    void signatory(Signatory signatory) {
        this.signatory = signatory
    }
    
    /**
     * Change whether or not this task should fail if no signatory or signature type are configured at the time of generation.
     */
    void required(boolean required) {
        setRequired(required)
    }
    
    /**
     * Generates the signature files.
     */
    @TaskAction
    void generate() {
        if (getSignatory() == null) {
            throw new InvalidUserDataException("Cannot perform signing task '${getPath()}' because it has no configured signatory")
        }
        
        getSignatures()*.generate()
    }
    
    /**
     * The signatures generated by this task.
     */
    DomainObjectSet<Signature> getSignatures() {
        signatures
    }
    
    /**
     * Returns the single signature generated by this task.
     *
     * @return The signature.
     * @throws IllegalStateException if there is not exactly one signature.
     */
    Signature getSingleSignature() {
        def signatureSet = getSignatures()
        if (signatureSet.size() == 0) {
            throw new IllegalStateException("Expected %s to contain exactly one signature, however, it contains no signatures.")
        } else if (signatureSet.size() == 1) {
            signatureSet.toList().first()
        } else {
            throw new IllegalStateException("Expected %s to contain exactly one signature, however, it contains no ${signatureSet.size()} signatures.")
        }
    }
    
    /**
     * All of the files that will be signed by this task.
     */
    FileCollection getFilesToSign() {
        new SimpleFileCollection(*getSignatures()*.toSign.findAll({ it != null }))
    }
    
    /**
     * All of the signature files that will be generated by this operation.
     */
    FileCollection getSignatureFiles() {
        new SimpleFileCollection(*getSignatures()*.file.findAll({ it != null }))
    }
}