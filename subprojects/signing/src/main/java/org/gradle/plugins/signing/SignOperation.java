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
package org.gradle.plugins.signing;

import com.google.common.base.Function;
import groovy.lang.Closure;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.file.FileCollection;
import org.gradle.plugins.signing.signatory.Signatory;
import org.gradle.plugins.signing.type.SignatureType;
import org.gradle.util.ConfigureUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * A sign operation creates digital signatures for one or more files or {@link PublishArtifact publish artifacts}.
 *
 * <p>The external representation of the signature is specified by the {@link #getSignatureType() signature type property}, while the {@link #signatory} property specifies who is to sign. <p> A sign
 * operation manages one or more {@link Signature} objects. The {@code sign} methods are used to register things to generate signatures for. The {@link #execute()} method generates the signatures for
 * all of the registered items at that time.
 */
abstract public class SignOperation implements SignatureSpec {

    /**
     * The file representation of the signature(s).
     */
    private SignatureType signatureType;

    /**
     * The signatory to the generated digital signatures.
     */
    private Signatory signatory;

    /**
     * Whether or not it is required that this signature be generated.
     */
    private boolean required;

    private final List<Signature> signatures = new ArrayList<Signature>();

    public String getDisplayName() {
        return "SignOperation";
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    @Override
    public void setSignatureType(SignatureType signatureType) {
        this.signatureType = signatureType;
    }

    @Override
    public SignatureType getSignatureType() {
        return signatureType;
    }

    @Override
    public void setSignatory(Signatory signatory) {
        this.signatory = signatory;
    }

    @Override
    public Signatory getSignatory() {
        return signatory;
    }

    @Override
    public void setRequired(boolean required) {
        this.required = required;
    }

    @Override
    public boolean isRequired() {
        return required;
    }

    /**
     * Registers signatures for the given artifacts.
     *
     * @return this
     * @see Signature#Signature(File, SignatureSpec, Object...)
     */
    public SignOperation sign(PublishArtifact... artifacts) {
        for (PublishArtifact artifact : artifacts) {
            signatures.add(new Signature(artifact, this));
        }
        return this;
    }

    /**
     * Registers signatures for the given files.
     *
     * @return this
     * @see Signature#Signature(File, SignatureSpec, Object...)
     */
    public SignOperation sign(File... files) {
        for (File file : files) {
            signatures.add(new Signature(file, this));
        }
        return this;
    }

    /**
     * Registers signatures (with the given classifier) for the given files
     *
     * @return this
     * @see Signature#Signature(PublishArtifact, SignatureSpec, Object...)
     */
    public SignOperation sign(String classifier, File... files) {
        for (File file : files) {
            signatures.add(new Signature(file, classifier, this));
        }
        return this;
    }

    /**
     * Change the signature type for signature generation.
     */
    public SignOperation signatureType(SignatureType type) {
        this.signatureType = type;
        return this;
    }

    /**
     * Change the signatory for signature generation.
     */
    public SignOperation signatory(Signatory signatory) {
        this.signatory = signatory;
        return this;
    }

    /**
     * Executes the given closure against this object.
     */
    public SignOperation configure(Closure closure) {
        ConfigureUtil.configureSelf(closure, this);
        return this;
    }

    /**
     * Generates actual signature files for all of the registered signatures.
     *
     * <p>The signatures are generated with the configuration they have at this time, which includes the signature type and signatory of this operation at this time. <p> This method can be called
     * multiple times, with the signatures being generated with their current configuration each time.
     *
     * @return this
     * @see Signature#generate()
     */
    public SignOperation execute() {
        for (Signature signature : signatures) {
            signature.generate();
        }
        return this;
    }

    /**
     * The registered signatures.
     */
    public List<Signature> getSignatures() {
        return new ArrayList<Signature>(signatures);
    }

    /**
     * Returns the single registered signature.
     *
     * @return The signature.
     * @throws IllegalStateException if there is not exactly one registered signature.
     */
    public Signature getSingleSignature() {
        final int size = signatures.size();
        switch (size) {
            case 1:
                return signatures.get(0);
            case 0:
                throw new IllegalStateException("Expected operation to contain exactly one signature, however, it contains no signatures.");
            default:
                throw new IllegalStateException("Expected operation to contain exactly one signature, however, it contains " + String.valueOf(size) + " signatures.");
        }
    }

    /**
     * All of the files that will be signed by this operation.
     */
    public FileCollection getFilesToSign() {
        return newSignatureFileCollection(input -> input.getToSign());
    }

    /**
     * All of the signature files that will be generated by this operation.
     */
    public FileCollection getSignatureFiles() {
        return newSignatureFileCollection(input -> input.getFile());
    }

    private FileCollection newSignatureFileCollection(Function<Signature, File> getFile) {
        return toFileCollection(collectSignatureFiles(getFile));
    }

    protected abstract FileCollection toFileCollection(List<File> files);

    private List<File> collectSignatureFiles(Function<Signature, File> getFile) {
        List<File> files = new ArrayList<File>(signatures.size());
        for (Signature signature : signatures) {
            File file = getFile.apply(signature);
            if (file != null) {
                files.add(file);
            }
        }
        return files;
    }
}
