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
import org.gradle.api.internal.artifacts.publish.AbstractPublishArtifact

import org.gradle.plugins.signing.signatory.Signatory
import org.gradle.plugins.signing.type.SignatureType

import org.gradle.api.InvalidUserDataException

class Signature extends AbstractPublishArtifact {
    
    /**
     * The specification of how to generate the signature
     */
    final SignatureSpec signatureSpec
    
    final PublishArtifact toSignArtifact
    
    private final Closure toSignGenerator
    private final Closure classifierGenerator
    
    // Overrides for the calculated values
    String name
    String extension
    String type
    String classifier
    Date date
    File file
    
    Signature(PublishArtifact toSign, SignatureSpec signatureSpec, Object... tasks) {
        this({ toSign.file }, { toSign.classifier }, signatureSpec, tasks)
        this.toSignArtifact = toSign
    }

    Signature(File toSign, SignatureSpec signatureSpec, Object... tasks) {
        this({ toSign }, null, signatureSpec, tasks)
    }
    
    Signature(File toSign, String classifier, SignatureSpec signatureSpec, Object... tasks) {
        this({ toSign }, { classifier }, signatureSpec, tasks)
    }
    
    Signature(Closure toSign, Closure classifier, SignatureSpec signatureSpec, Object... tasks) {
        super(tasks)
        this.toSignGenerator = toSign
        this.classifierGenerator = classifier
        this.signatureSpec = signatureSpec
    }
    
    File getToSign() {
        def toSign = toSignGenerator?.call()
        if (toSign == null) {
            null
        } else if (toSign instanceof File) {
            toSign
        } else {
            new File(toSign.toString())
        }
    }

    String getName() {
        name == null ? getFile()?.name : name
    }
    
    String getExtension() {
        extension == null ? getSignatureType()?.extension : extension
    }

    String getType() {
        if (type == null) {
            def toSign = getToSign()
            def signatureType = getSignatureType()

            if (toSign != null && signatureType != null) {
                signatureType.combinedExtension(toSign)
            } else {
                null
            }
        } else {
            type
        }
    }

    String getClassifier() {
        classifier == null ? classifierGenerator?.call()?.toString() : classifier
    }

    Date getDate() {
        if (date == null) {
            def file = getFile()
            if (file == null) {
                null
            } else {
                def modified = file.lastModified()
                if (modified == 0) {
                    null
                } else {
                    new Date(modified)
                }
            }
        } else {
            date
        }
    }
    
    File getFile() {
        if (file == null) {
            def toSign = getToSign()
            def signatureType = getSignatureType()

            if (toSign != null && signatureType != null) {
                signatureType.fileFor(toSign)
            } else {
                null
            }
        } else {
            file
        }
    }
    
    Signatory getSignatory() {
        signatureSpec.getSignatory()
    }
    
    SignatureType getSignatureType() {
        signatureSpec.getSignatureType()
    }
    
    void generate() {
        def toSign = getToSign()
        if (toSign == null) {
            throw new InvalidUserDataException("Unable to generate signature as the file to sign has not been specified")
        }
        
        def signatory = getSignatory()
        if (signatory == null) {
            throw new InvalidUserDataException("Unable to generate signature for '$toSign' as no signatory is available to sign")
        }
        
        def signatureType = getSignatureType()
        if (signatureType == null) {
            throw new InvalidUserDataException("Unable to generate signature for '$toSign' as no signature type has been configured")
        }
        
        signatureType.sign(signatory, toSign)
    }
    
}