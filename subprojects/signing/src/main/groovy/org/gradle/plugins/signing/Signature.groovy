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

/**
 * A digital signature file artifact.
 * 
 * <p>A signature file is always generated from another file, which may be a {@link PublishArtifact}.</p>
 */
class Signature extends AbstractPublishArtifact {
    
    /**
     * The specification of how to generate the signature.
     */
     SignatureSpec signatureSpec
    
    /**
     * The artifact that this signature is for, which may be {@code null} if this signature is not for an artifact.
     */
    final PublishArtifact toSignArtifact
    
    /**
     * The name of the signature artifact.
     * 
     * @see #getName()
     */
    String name
    
    /**
     * The extension of the signature artifact.
     * 
     * @see #getExtension()
     */
    String extension
    
    /**
     * The type of the signature artifact.
     * 
     * @see #getType()
     */
    String type
    
    /**
     * The classifier of the signature artifact.
     * 
     * @see #getClassifier()
     */
    String classifier
    
    /**
     * The date of the signature arifact.
     * 
     * @see #getDate()
     */
    Date date
    
    /**
     * The signature file.
     * 
     * @see #getFile()
     */
    File file

    /*
        To fully support laziness, we store callbacks for what is to be signed and the classifier for what is to be signed.
        
        This is necessary because we may be signing an ArchivePublishArtifact which is “live” in that it's tied to an AbstractArchiveTask
        which may be modified after the user has declared that they want the artifact signed
    */
    private final Closure toSignGenerator
    private final Closure classifierGenerator
    
    /**
     * Creates a signature artifact for the given public artifact.
     * 
     * <p>The file to sign will be the file of the given artifact and the classifier of this signature artifact
     * will default to the classifier of the given artifact to sign.</p>
     * <p>
     * The artifact to sign may change after being used as the source for this signature.</p>
     * 
     * @param toSign The artifact that is to be signed
     * @param signatureSpec The specification of how the artifact is to be signed
     * @param tasks The task(s) that will invoke {@link #generate()} on this signature (optional)
     */
    Signature(PublishArtifact toSign, SignatureSpec signatureSpec, Object... tasks) {
        this({ toSign.file }, { toSign.classifier }, signatureSpec, tasks)
        this.toSignArtifact = toSign
    }

    /**
     * Creates a signature artifact for the given file.
     * 
     * @param toSign The file that is to be signed
     * @param signatureSpec The specification of how the artifact is to be signed
     * @param tasks The task(s) that will invoke {@link #generate()} on this signature (optional)
     */
    Signature(File toSign, SignatureSpec signatureSpec, Object... tasks) {
        this({ toSign }, null, signatureSpec, tasks)
    }

    /**
     * Creates a signature artifact for the given file, with the given classifier.
     * 
     * @param toSign The file that is to be signed
     * @param classifier The classifier to assign to the signature (should match the files)
     * @param signatureSpec The specification of how the artifact is to be signed
     * @param tasks The task(s) that will invoke {@link #generate()} on this signature (optional)
     */
    Signature(File toSign, String classifier, SignatureSpec signatureSpec, Object... tasks) {
        this({ toSign }, { classifier }, signatureSpec, tasks)
    }
    
    /**
     * Creates a signature artifact for the file returned by the {@code toSign} closure.
     * 
     * <p>The closures will be “evaluated” on demand whenever the value is needed (e.g. at generation time)</p>
     * 
     * @param toSign A closure that produces a File for the object to sign (non File return values will be used as the path to the file)
     * @param classifier A closure that produces the classifier to assign to the signature artifact on demand
     * @param signatureSpec The specification of how the artifact is to be signed
     * @param tasks The task(s) that will invoke {@link #generate()} on this signature (optional)
     */
    Signature(Closure toSign, Closure classifier, SignatureSpec signatureSpec, Object... tasks) {
        super(tasks)
        this.toSignGenerator = toSign
        this.classifierGenerator = classifier
        this.signatureSpec = signatureSpec
    }
    
    /**
     * The file that is to be signed.
     * 
     * @return The file. May be {@code null} if unknown at this time.
     */
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

    /**
     * The name of the signature artifact.
     * 
     * <p>Defaults to the name of the signature {@link #getFile() file}.
     * 
     * @return The name. May be {@code null} if unknown at this time.
     */
    String getName() {
        name == null ? (toSignArtifact?.name ?: getFile()?.name) : name
    }
    
    /**
     * The extension of the signature artifact.
     * 
     * <p>Defaults to the specified file extension of the {@link #getSignatureType() signature type}.</p>
     * 
     * @return The extension. May be {@code null} if unknown at this time.
     */
    String getExtension() {
        extension == null ? getSignatureType()?.extension : extension
    }

    /**
     * The type of the signature artifact.
     * 
     * <p>Defaults to the extension of the {@link #getToSign() file to sign} plus the extension of the {@link #getSignatureType() signature type}.
     * For example, when signing the file ‘my.zip’ with a signature type with extension ‘sig’, the default type is ‘zip.sig’.</p>
     * 
     * @return The type. May be {@code null} if the file to sign or signature type are unknown at this time.
     */
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

    /**
     * The classifier of the signature artifact.
     * 
     * <p>Defaults to the classifier of the source artifact (if signing an artifact) 
     * or the given classifier at construction (if given).</p>
     * 
     * @return The classifier. May be {@code null} if unknown at this time.
     */
    String getClassifier() {
        classifier == null ? classifierGenerator?.call()?.toString() : classifier
    }

    /**
     * The date of the signature artifact.
     * 
     * <p>Defaults to the last modified time of the {@link #getFile() signature file} (if exists)</p>
     * 
     * @return The date of the signature. May be {@code null} if unknown at this time.
     */
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
    
    /**
     * The file for the generated signature, which may not yet exist.
     * 
     * <p>Defaults to a file alongside the {@link #getToSign() file to sign} with the extension of the {@link #getSignatureType() signature type}.</p>
     * 
     * @return The signature file. May be {@code null} if unknown at this time.
     */
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
    
    /**
     * The signatory of this signature file.
     * 
     * @return The signatory. May be {@code null} if unknown at this time.
     */
    Signatory getSignatory() {
        signatureSpec.getSignatory()
    }
    
    /**
     * The file representation type of the signature.
     * 
     * @return The signature type. May be {@code null} if unknown at this time.
     */
    SignatureType getSignatureType() {
        signatureSpec.getSignatureType()
    }
    
    /**
     * Generates the signature file.
     * 
     * <p>In order to generate the signature, the {@link #getToSign() file to sign}, {@link #getSignatory() signatory} and {@link #getSignatureType() signature type}
     * must be known (i.e. non {@code null}).</p>
     * 
     * @throws InvalidUserDataException if the there is insufficient information available to generate the signature.
     */
    void generate() {
        def toSign = getToSign()
        if (toSign == null) {
            if (signatureSpec.required) {
                throw new InvalidUserDataException("Unable to generate signature as the file to sign has not been specified")
            } else {
                return
            }
        }
        
        def signatory = getSignatory()
        if (signatory == null) {
            if (signatureSpec.required) {
                throw new InvalidUserDataException("Unable to generate signature for '$toSign' as no signatory is available to sign")
            } else {
                return
            }
        }
        
        def signatureType = getSignatureType()
        if (signatureType == null) {
            if (signatureSpec.required) {
                throw new InvalidUserDataException("Unable to generate signature for '$toSign' as no signature type has been configured")
            } else {
                return
            }
        }
        
        signatureType.sign(signatory, toSign)
    }
    
}