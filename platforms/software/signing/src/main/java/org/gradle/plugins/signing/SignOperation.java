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

import groovy.lang.Closure;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.file.FileCollection;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.instrumentation.api.annotations.NotToBeReplacedByLazyProperty;
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty;
import org.gradle.plugins.signing.signatory.Signatory;
import org.gradle.plugins.signing.type.SignatureType;
import org.gradle.util.internal.ConfigureUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * A sign operation creates digital signatures for one or more files.
 * <p>
 * The external representation of the signature is specified by {@link #getSignatureType()}, while the
 * {@link #getSignatory()} specifies how to sign. The {@code sign} methods are used to register files to
 * generate signatures for. The {@link #execute()} method generates the signatures for all the registered
 * items at that time.
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

    private final List<Supplier<File>> fileSources = new ArrayList<>();

    @ToBeReplacedByLazyProperty
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
    @ToBeReplacedByLazyProperty
    public SignatureType getSignatureType() {
        return signatureType;
    }

    @Override
    public void setSignatory(Signatory signatory) {
        this.signatory = signatory;
    }

    @Override
    @ToBeReplacedByLazyProperty
    public Signatory getSignatory() {
        return signatory;
    }

    @Override
    public void setRequired(boolean required) {
        this.required = required;
    }

    @Override
    @ToBeReplacedByLazyProperty
    public boolean isRequired() {
        return required;
    }

    /**
     * Registers signatures for the given artifacts.
     *
     * @return this
     */
    public SignOperation sign(PublishArtifact... artifacts) {
        for (PublishArtifact artifact : artifacts) {
            fileSources.add(artifact::getFile);
        }
        return this;
    }

    /**
     * Registers signatures for the given files.
     *
     * @return this
     */
    public SignOperation sign(File... files) {
        for (File file : files) {
            fileSources.add(() -> file);
        }
        return this;
    }

    /**
     * Registers signatures (with the given classifier) for the given files
     *
     * @return this
     *
     * @deprecated This method will be removed in Gradle 10. Use {@link #sign(File...)} instead.
     */
    @Deprecated
    public SignOperation sign(String ignoredClassifier, File... files) {
        DeprecationLogger.deprecateMethod(SignOperation.class, "sign(String, File...)")
            .withAdvice("Use sign(File...) instead.")
            .willBeRemovedInGradle10()
            .withUpgradeGuideSection(9, "deprecate_sign_classifier")
            .nagUser();

        for (File file : files) {
            fileSources.add(() -> file);
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
     */
    public SignOperation execute() {
        for (Supplier<File> file : fileSources) {
            signatureType.sign(signatory, file.get());
        }
        return this;
    }

    /**
     * The registered signatures.
     *
     * @deprecated This method will be removed in Gradle 10. Use {@link #getFilesToSign()} or {@link #getSignatureFiles()} instead.
     */
    @Deprecated
    public List<Signature> getSignatures() {
        DeprecationLogger.deprecateMethod(SignOperation.class, "getSignatures()")
            .withAdvice("Use getFilesToSign() or getSignatureFiles() instead.")
            .willBeRemovedInGradle10()
            .withUpgradeGuideSection(9, "deprecate_sign_operation_signatures")
            .nagUser();

        return fileSources.stream().map(fileSource ->
            DeprecationLogger.whileDisabled(() ->
                new Signature(null, fileSource::get, null, null, this)
            )
        ).toList();
    }

    /**
     * Returns the single registered signature.
     *
     * @return The signature.
     * @throws IllegalStateException if there is not exactly one registered signature.
     *
     * @deprecated This method will be removed in Gradle 10. Use {@link #getFilesToSign()} or {@link #getSignatureFiles()} instead.
     */
    @Deprecated
    public Signature getSingleSignature() {
        DeprecationLogger.deprecateMethod(SignOperation.class, "getSingleSignature()")
            .withAdvice("Use getFilesToSign() or getSignatureFiles() instead.")
            .willBeRemovedInGradle10()
            .withUpgradeGuideSection(9, "deprecate_sign_operation_signatures")
            .nagUser();

        List<Signature> signatures = DeprecationLogger.whileDisabled(this::getSignatures);
        if (signatures.size() != 1) {
            throw new IllegalStateException("Expected operation to contain exactly one signature, however, it contains " + signatures.size() + " signatures.");
        }
        return signatures.get(0);
    }

    /**
     * All of the files that will be signed by this operation.
     */
    @NotToBeReplacedByLazyProperty(because = "Read-only file collection")
    public FileCollection getFilesToSign() {
        return toFileCollection(
            fileSources.stream()
                .map(Supplier::get)
                .toList()
        );
    }

    /**
     * All of the signature files that will be generated by this operation.
     */
    @NotToBeReplacedByLazyProperty(because = "Read-only file collection")
    public FileCollection getSignatureFiles() {
        return toFileCollection(
            fileSources.stream()
                .map(fileSource -> signatureType.fileFor(fileSource.get()))
                .toList()
        );
    }

    protected abstract FileCollection toFileCollection(List<File> files);

}
