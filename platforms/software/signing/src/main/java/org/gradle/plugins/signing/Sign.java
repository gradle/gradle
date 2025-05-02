/*
 * Copyright 2016 the original author or authors.
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

import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.gradle.api.Buildable;
import org.gradle.api.DefaultTask;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.Incubating;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.publish.Publication;
import org.gradle.api.publish.PublicationArtifact;
import org.gradle.api.publish.internal.PublicationInternal;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.internal.instrumentation.api.annotations.NotToBeReplacedByLazyProperty;
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty;
import org.gradle.internal.serialization.Cached;
import org.gradle.internal.serialization.Transient;
import org.gradle.plugins.signing.signatory.Signatory;
import org.gradle.plugins.signing.type.SignatureType;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.gradle.api.internal.lambdas.SerializableLambdas.spec;

/**
 * A task for creating digital signature files for one or more; tasks, files, publishable artifacts or configurations.
 *
 * <p>The task produces {@link Signature}</p> objects that are publishable artifacts and can be assigned to another configuration. <p> The signature objects are created with defaults and using this
 * tasks signatory and signature type.
 */
@DisableCachingByDefault(because = "Not made cacheable, yet")
public abstract class Sign extends DefaultTask implements SignatureSpec {

    private SignatureType signatureType;
    private Signatory signatory;
    private boolean required = true;
    private final Transient<DomainObjectSet<Signature>> signatures = Transient.of(getProject().getObjects().domainObjectSet(Signature.class));

    private final Cached<Collection<Signature.Generator>> generators = Cached.of(this::computeCachedSignatures);

    private List<Signature.Generator> computeCachedSignatures() {
        if (getSignatory() == null) {
            // The task will fail, so we don't need any state.
            return Collections.emptyList();
        }
        return signatureStream()
            .map(Signature::getGenerator)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    private Stream<Signature> signatureStream() {
        return getSignatures().stream();
    }

    @Inject
    public Sign() {
        // If we aren't required and don't have a signatory then we just don't run
        onlyIf("Signing is required, or signatory is set", spec(task -> isRequired() || getSignatory() != null));
    }

    /**
     * Configures the task to sign the archive produced for each of the given tasks (which must be archive tasks).
     */
    public void sign(Task... tasks) {
        for (Task task : tasks) {
            if (!(task instanceof AbstractArchiveTask)) {
                throw new InvalidUserDataException("You cannot sign tasks that are not \'archive\' tasks, such as \'jar\', \'zip\' etc. (you tried to sign " + task + ")");
            }

            signTask((AbstractArchiveTask) task);
        }
    }

    private void signTask(final AbstractArchiveTask archiveTask) {
        dependsOn(archiveTask);
        addSignature(new Signature(() -> archiveTask.getArchiveFile().get().getAsFile(), () -> archiveTask.getArchiveClassifier().getOrNull(), this, this));
    }

    /**
     * Configures the task to sign each of the given artifacts
     */
    public void sign(PublishArtifact... publishArtifacts) {
        for (PublishArtifact publishArtifact : publishArtifacts) {
            signArtifact(publishArtifact);
        }
    }

    private void signArtifact(PublishArtifact publishArtifact) {
        dependsOn(publishArtifact);
        addSignature(new Signature(publishArtifact, this, this));
    }

    /**
     * Configures the task to sign each of the given files
     */
    public void sign(File... files) {
        addSignatures(null, files);
    }

    /**
     * Configures the task to sign each of the given artifacts, using the given classifier as the classifier for the resultant signature publish artifact.
     */
    public void sign(String classifier, File... files) {
        addSignatures(classifier, files);
    }

    private void addSignatures(String classifier, File[] files) {
        for (File file : files) {
            addSignature(new Signature(file, classifier, this, this));
        }
    }

    /**
     * Configures the task to sign every artifact of the given configurations
     */
    public void sign(Configuration... configurations) {
        for (Configuration configuration : configurations) {
            configuration.getAllArtifacts().all(artifact -> {
                if (artifact instanceof Signature || hasSignatureFor(artifact)) {
                    return;
                }
                signArtifact(artifact);
            });
            configuration.getAllArtifacts().whenObjectRemoved(this::removeSignature);
        }
    }

    private boolean hasSignatureFor(Buildable source) {
        return signatureStream().anyMatch(hasSource(source));
    }

    /**
     * Configures the task to sign every artifact of the given publications
     *
     * @since 4.8
     */
    public void sign(Publication... publications) {
        for (Publication publication : publications) {
            PublicationInternal<?> publicationInternal = (PublicationInternal<?>) publication;
            dependsOn((Callable<Set<? extends PublicationArtifact>>) () -> {
                return publicationInternal.getPublishableArtifacts().matching(this::isNoSignatureArtifact);
            });
            publicationInternal.allPublishableArtifacts(artifact -> {
                if (isNoSignatureArtifact(artifact)) {
                    addSignature(new Signature(artifact, artifact::getFile, null, null, this, this));
                }
            });
            publicationInternal.whenPublishableArtifactRemoved(this::removeSignature);
        }
    }

    private boolean isNoSignatureArtifact(PublicationArtifact artifact) {
        return !getSignatureFiles().contains(artifact.getFile());
    }

    private void addSignature(Signature signature) {
        getSignatures().add(signature);
    }

    private void removeSignature(final Buildable source) {
        getSignatures().removeIf(hasSource(source));
    }

    private Predicate<Signature> hasSource(Buildable source) {
        return signature -> signature.getSource().equals(source);
    }

    /**
     * Changes the signatory of the signatures.
     */
    public void signatory(Signatory signatory) {
        this.signatory = signatory;
    }

    /**
     * Change whether or not this task should fail if no signatory or signature type are configured at the time of generation.
     */
    public void required(boolean required) {
        setRequired(required);
    }

    /**
     * Generates the signature files.
     */
    @TaskAction
    public void generate() {
        if (getSignatory() == null) {
            throw new InvalidUserDataException("Cannot perform signing task \'" + getPath() + "\' because it has no configured signatory");
        }

        for (Signature.Generator signature : sanitizedGenerators().values()) {
            signature.generate();
        }
    }

    /**
     * The signatures generated by this task.
     */
    @Internal
    public DomainObjectSet<Signature> getSignatures() {
        return signatures.get();
    }

    /**
     * The signature generators for this task mapped by a unique key used for up-to-date checking.
     *
     * @since 8.1
     */
    @Nested
    @Incubating
    public Map<String, Signature.Generator> getGeneratorsByKey() {
        return sanitizedGenerators();
    }

    /**
     * The signatures generated by this task mapped by a unique key used for up-to-date checking.
     *
     * @since 5.1
     */
    @Internal
    @ToBeReplacedByLazyProperty
    public Map<String, Signature> getSignaturesByKey() {
        return sanitizedSignatures();
    }

    private Map<String, Signature.Generator> sanitizedGenerators() {
        return sanitize(
            signatures.isPresent()
                ? computeCachedSignatures()
                : generators.get()
        );
    }

    /**
     * Returns signature generators mapped by their key with duplicated and non-existing inputs removed.
     */
    private static Map<String, Signature.Generator> sanitize(Collection<Signature.Generator> generators) {
        return generators.stream()
            .filter(signature -> signature.getToSign().exists())
            .collect(
                toMap(
                    signature -> signature.getToSign().toPath().toAbsolutePath().toString(),
                    identity(),
                    (signature, duplicate) -> signature
                )
            );
    }

    /**
     * Returns the single signature generated by this task.
     *
     * @return The signature.
     * @throws IllegalStateException if there is not exactly one signature.
     */
    @Internal
    @ToBeReplacedByLazyProperty
    public Signature getSingleSignature() {
        Map<String, Signature> sanitizedSignatures = sanitizedSignatures();
        if (sanitizedSignatures.size() == 1) {
            return sanitizedSignatures.values().iterator().next();
        }
        throw new IllegalStateException("Expected %s to contain exactly one signature, however, it contains " + sanitizedSignatures.size() + " signatures.");
    }

    private Map<String, Signature> sanitizedSignatures() {
        return getSignatures().matching(signature -> signature.getToSign().exists())
            .stream()
            .collect(
                toMap(
                    signature -> signature.getToSign().toPath().toAbsolutePath().toString(),
                    identity(),
                    (signature, duplicate) -> signature
                )
            );
    }

    @Inject
    protected FileCollectionFactory getFileCollectionFactory() {
        // Implementation provided by decoration
        throw new UnsupportedOperationException();
    }

    /**
     * All of the files that will be signed by this task.
     */
    @Internal
    @NotToBeReplacedByLazyProperty(because = "Read-only file collection", willBeDeprecated = true)
    public FileCollection getFilesToSign() {
        return getFileCollectionFactory().fixed("Task \'" + getPath() + "\' files to sign",
            Lists.newLinkedList(Iterables.filter(Iterables.transform(getSignatures(), Signature::getToSign), Predicates.notNull())));
    }

    /**
     * All of the signature files that will be generated by this operation.
     */
    @Internal
    @NotToBeReplacedByLazyProperty(because = "Read-only file collection", willBeDeprecated = true)
    public FileCollection getSignatureFiles() {
        return getFileCollectionFactory().fixed("Task \'" + getPath() + "\' signature files",
            Lists.newLinkedList(Iterables.filter(Iterables.transform(getSignatures(), Signature::getFile), Predicates.notNull())));
    }

    @Override
    @Nested
    @Optional
    public SignatureType getSignatureType() {
        return signatureType;
    }

    @Override
    public void setSignatureType(SignatureType signatureType) {
        this.signatureType = signatureType;
    }

    /**
     * Returns the signatory for this signing task.
     *
     * @return the signatory
     */
    @Override
    @Nested
    @Optional
    public Signatory getSignatory() {
        return signatory;
    }

    @Override
    public void setSignatory(Signatory signatory) {
        this.signatory = signatory;
    }

    /**
     * Whether or not this task should fail if no signatory or signature type are configured at generation time.
     *
     * <p>Defaults to {@code true}.</p>
     */
    @Override
    @Input
    @ToBeReplacedByLazyProperty
    public boolean isRequired() {
        return required;
    }

    @Override
    public void setRequired(boolean required) {
        this.required = required;
    }

    /**
     * Required for decorating reports container callbacks for tracing user code application.
     *
     * @since 5.1
     */
    @Inject
    protected CollectionCallbackActionDecorator getCallbackActionDecorator() {
        throw new UnsupportedOperationException();
    }
}
