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
import org.gradle.api.Buildable;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.internal.artifacts.publish.AbstractPublishArtifact;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.plugins.signing.signatory.Signatory;
import org.gradle.plugins.signing.type.SignatureType;

import javax.annotation.Nullable;
import java.io.File;
import java.util.Date;
import java.util.concurrent.Callable;

import static com.google.common.util.concurrent.Callables.returning;
import static org.gradle.util.internal.GUtil.uncheckedCall;

/**
 * A digital signature file artifact.
 *
 * <p>A signature file is always generated from another file, which may be a {@link PublishArtifact}.</p>
 */
public class Signature extends AbstractPublishArtifact {

    /**
     * The specification of how to generate the signature.
     */
    private SignatureSpec signatureSpec;

    /**
     * The artifact that this signature is for, which may be {@code null} if this signature is not for an artifact.
     */
    private Buildable source;

    /**
     * The name of the signature artifact.
     *
     * @see #getName()
     */
    private String name;

    /**
     * The extension of the signature artifact.
     *
     * @see #getExtension()
     */
    private String extension;

    /**
     * The type of the signature artifact.
     *
     * @see #getType()
     */
    private String type;

    /**
     * The classifier of the signature artifact.
     *
     * @see #getClassifier()
     */
    private String classifier;

    /**
     * The date of the signature artifact.
     *
     * @see #getDate()
     */
    private Date date;

    private Callable<File> toSignGenerator;

    private Callable<String> classifierGenerator;

    private Callable<String> nameGenerator;

    /**
     * Creates a signature artifact for the given public artifact.
     *
     * <p>The file to sign will be the file of the given artifact and the classifier of this signature artifact will default to the classifier of the given artifact to sign.</p> <p> The artifact to
     * sign may change after being used as the source for this signature.</p>
     *
     * @param toSign The artifact that is to be signed
     * @param signatureSpec The specification of how the artifact is to be signed
     * @param tasks The task(s) that will invoke {@link #generate()} on this signature (optional)
     */
    public Signature(final PublishArtifact toSign, SignatureSpec signatureSpec, Object... tasks) {
        this(toSign, new Callable<File>() {
            @Override
            public File call() {
                return toSign.getFile();
            }
        }, new Callable<String>() {
            @Override
            public String call() {
                return toSign.getClassifier();
            }
        }, new Callable<String>() {
            @Override
            public String call() {
                return toSign.getName();
            }
        }, signatureSpec, tasks);
    }

    Signature(Buildable source, Callable<File> toSign, Callable<String> classifier, Callable<String> name, SignatureSpec signatureSpec, Object... tasks) {
        super(tasks);
        init(toSign, classifier, name, signatureSpec);
        this.source = source;
    }

    /**
     * Creates a signature artifact for the given file.
     *
     * @param toSign The file that is to be signed
     * @param signatureSpec The specification of how the artifact is to be signed
     * @param tasks The task(s) that will invoke {@link #generate()} on this signature (optional)
     */
    public Signature(final File toSign, SignatureSpec signatureSpec, Object... tasks) {
        super(tasks);
        init(returning(toSign), null, null, signatureSpec);
    }

    /**
     * Creates a signature artifact for the given file, with the given classifier.
     *
     * @param toSign The file that is to be signed
     * @param classifier The classifier to assign to the signature (should match the files)
     * @param signatureSpec The specification of how the artifact is to be signed
     * @param tasks The task(s) that will invoke {@link #generate()} on this signature (optional)
     */
    public Signature(final File toSign, final String classifier, SignatureSpec signatureSpec, Object... tasks) {
        super(tasks);
        init(returning(toSign), returning(classifier), null, signatureSpec);
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
    public Signature(Closure<File> toSign, Closure<String> classifier, SignatureSpec signatureSpec, Object... tasks) {
        super(tasks);
        this.toSignGenerator = toSign;
        this.classifierGenerator = classifier;
        this.signatureSpec = signatureSpec;
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
    public Signature(Callable<File> toSign, Callable<String> classifier, SignatureSpec signatureSpec, Object... tasks) {
        super(tasks);
        this.toSignGenerator = toSign;
        this.classifierGenerator = classifier;
        this.signatureSpec = signatureSpec;
    }

    private void init(Callable<File> toSign, Callable<String> classifier, Callable<String> name, SignatureSpec signatureSpec) {
        this.toSignGenerator = toSign;
        this.classifierGenerator = classifier;
        this.nameGenerator = name;
        this.signatureSpec = signatureSpec;
    }

    /**
     * The file that is to be signed.
     *
     * @return The file. May be {@code null} if unknown at this time.
     */
    @PathSensitive(PathSensitivity.NONE)
    @InputFile
    public File getToSign() {
        File toSign = uncheckedCall(toSignGenerator);
        return toSign != null ? toSign : null;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * The name of the signature artifact.
     *
     * <p>Defaults to the name of the signature {@link #getFile() file}.
     *
     * @return The name. May be {@code null} if unknown at this time.
     */
    @Override
    @Internal
    public String getName() {
        return name != null ? name : defaultName();
    }

    @Nullable
    private String defaultName() {
        return nameGenerator != null ? uncheckedCall(nameGenerator) : fileName();
    }

    @Nullable
    private String fileName() {
        final File file = getFile();
        return file != null ? file.getName() : null;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    /**
     * The extension of the signature artifact.
     *
     * <p>Defaults to the specified file extension of the {@link #getSignatureType() signature type}.</p>
     *
     * @return The extension. May be {@code null} if unknown at this time.
     */
    @Override
    @Internal
    public String getExtension() {
        return extension != null ? extension : signatureTypeExtension();
    }

    @Nullable
    private String signatureTypeExtension() {
        SignatureType signatureType = getSignatureType();
        return signatureType != null ? signatureType.getExtension() : null;
    }

    public void setType(String type) {
        this.type = type;
    }

    /**
     * The type of the signature artifact.
     *
     * <p>Defaults to the extension of the {@link #getToSign() file to sign} plus the extension of the {@link #getSignatureType() signature type}. For example, when signing the file ‘my.zip’ with a
     * signature type with extension ‘sig’, the default type is ‘zip.sig’.</p>
     *
     * @return The type. May be {@code null} if the file to sign or signature type are unknown at this time.
     */
    @Override
    @Internal
    public String getType() {
        return type != null ? type : defaultType();
    }

    @Nullable
    private String defaultType() {
        File toSign = getToSign();
        SignatureType signatureType = getSignatureType();
        return toSign != null && signatureType != null
            ? signatureType.combinedExtension(toSign)
            : null;
    }

    public void setClassifier(String classifier) {
        this.classifier = classifier;
    }

    /**
     * The classifier of the signature artifact.
     *
     * <p>Defaults to the classifier of the source artifact (if signing an artifact) or the given classifier at construction (if given).</p>
     *
     * @return The classifier. May be {@code null} if unknown at this time.
     */
    @Override
    @Internal
    public String getClassifier() {
        return classifier != null ? classifier : defaultClassifier();
    }

    private String defaultClassifier() {
        return classifierGenerator == null ? null : uncheckedCall(classifierGenerator);
    }

    public void setDate(Date date) {
        this.date = date;
    }

    /**
     * The date of the signature artifact.
     *
     * <p>Defaults to the last modified time of the {@link #getFile() signature file} (if exists)</p>
     *
     * @return The date of the signature. May be {@code null} if unknown at this time.
     */
    @Override
    @Internal
    public Date getDate() {
        return date != null ? date : defaultDate();
    }

    @Nullable
    private Date defaultDate() {
        File file = getFile();
        if (file == null) {
            return null;
        }

        long modified = file.lastModified();
        if (modified == 0L) {
            return null;
        }

        return new Date(modified);
    }

    /**
     * The file for the generated signature, which may not yet exist.
     *
     * <p>The file will be placed alongside the {@link #getToSign() file to sign} with the extension of the {@link #getSignatureType() signature type}.
     *
     * @return The signature file. May be {@code null} if unknown at this time.
     * @see SignatureType#fileFor(File)
     */
    @Override
    @OutputFile
    public File getFile() {
        File toSign = getToSign();
        SignatureType signatureType = getSignatureType();
        return toSign != null && signatureType != null
            ? signatureType.fileFor(toSign)
            : null;
    }

    /**
     * The signatory of this signature file.
     *
     * @return The signatory. May be {@code null} if unknown at this time.
     */
    @Internal("already tracked as part of the Sign task")
    public Signatory getSignatory() {
        return signatureSpec.getSignatory();
    }

    /**
     * The file representation type of the signature.
     *
     * @return The signature type. May be {@code null} if unknown at this time.
     */
    @Internal("already tracked as part of the Sign task")
    public SignatureType getSignatureType() {
        return signatureSpec.getSignatureType();
    }

    public void setSignatureSpec(SignatureSpec signatureSpec) {
        this.signatureSpec = signatureSpec;
    }

    @Internal
    public SignatureSpec getSignatureSpec() {
        return signatureSpec;
    }

    @Internal
    Buildable getSource() {
        return source;
    }

    @Internal
    @Override
    public TaskDependency getBuildDependencies() {
        return super.getBuildDependencies();
    }

    String toKey() {
        return String.join(":", getName(), getType(), getExtension(), getClassifier());
    }

    /**
     * Generates the signature file.
     *
     * <p>In order to generate the signature, the {@link #getToSign() file to sign}, {@link #getSignatory() signatory} and {@link #getSignatureType() signature type} must be known (i.e. non {@code
     * null}).</p>
     *
     * @throws InvalidUserDataException if the there is insufficient information available to generate the signature.
     */
    public void generate() {
        File toSign = getToSign();
        if (toSign == null) {
            if (signatureSpec.isRequired()) {
                throw new InvalidUserDataException("Unable to generate signature as the file to sign has not been specified");
            } else {
                return;
            }
        }

        Signatory signatory = getSignatory();
        if (signatory == null) {
            if (signatureSpec.isRequired()) {
                throw new InvalidUserDataException("Unable to generate signature for \'" + toSign + "\' as no signatory is available to sign");
            } else {
                return;
            }
        }

        SignatureType signatureType = getSignatureType();
        if (signatureType == null) {
            if (signatureSpec.isRequired()) {
                throw new InvalidUserDataException("Unable to generate signature for \'" + toSign + "\' as no signature type has been configured");
            } else {
                return;
            }
        }

        signatureType.sign(signatory, toSign);
    }

    @Override
    public boolean shouldBePublished() {
        return true;
    }
}
