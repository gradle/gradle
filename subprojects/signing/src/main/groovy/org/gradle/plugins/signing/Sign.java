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

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.DomainObjectSet;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.DefaultDomainObjectSet;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFiles;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.plugins.signing.signatory.Signatory;
import org.gradle.plugins.signing.signatory.pgp.PgpKeyId;
import org.gradle.plugins.signing.signatory.pgp.PgpSignatory;
import org.gradle.plugins.signing.type.SignatureType;

import javax.inject.Inject;
import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

/**
 * A task for creating digital signature files for one or more; tasks, files, publishable artifacts or configurations.
 *
 * <p>The task produces {@link Signature}</p> objects that are publishable artifacts and can be assigned to another configuration. <p> The signature objects are created with defaults and using this
 * tasks signatory and signature type.
 */
public class Sign extends DefaultTask implements SignatureSpec {

    private static final Pattern JAVA_PARTS = Pattern.compile("[^\\p{javaJavaIdentifierPart}]");

    private static final Function<Signature, File> SIGNATURE_TO_SIGN_FILE_FUNCTION = new Function<Signature, File>() {
        @Override
        public File apply(Signature input) {
            return input.getToSign();
        }
    };

    private static final Function<Signature, File> SIGNATURE_FILE_FUNCTION = new Function<Signature, File>() {
        @Override
        public File apply(Signature input) {
            return input.getFile();
        }
    };

    @Internal
    private SignatureType signatureType;
    /**
     * The signatory to the generated signatures.
     */
    @Internal
    private Signatory signatory;

    private boolean required = true;
    private final DefaultDomainObjectSet<Signature> signatures = new DefaultDomainObjectSet<Signature>(Signature.class);

    public Sign() {
        // If we aren't required and don't have a signatory then we just don't run
        onlyIf(new Spec<Task>() {
            @Override
            public boolean isSatisfiedBy(Task element) {
                return isRequired() || getSignatory() != null;
            }
        });

        getInputs().property("signatory", new Callable<String>() {
            @Override
            public String call() throws Exception {
                final PgpSignatory signatory = (PgpSignatory) getSignatory();
                final PgpKeyId id = signatory == null ? null : signatory.getKeyId();
                return id == null ? null : id.getAsHex();
            }
        });
    }

    @InputFiles
    public Iterable<File> getInputFiles() {
        return Iterables.transform(signatures, SIGNATURE_TO_SIGN_FILE_FUNCTION);
    }

    @OutputFiles
    public Map<String, File> getOutputFiles() {
        ArrayListMultimap<String, File> filesWithPotentialNameCollisions = ArrayListMultimap.create();
        for (Signature signature : getSignatures()) {
            String name = JAVA_PARTS.matcher(signature.getName()).replaceAll("_");
            if (name.length() > 0 && !Character.isJavaIdentifierStart(name.codePointAt(0))) {
                name = "_" + name.substring(1);
            }

            filesWithPotentialNameCollisions.put(name, signature.getToSign());
        }

        Map<String, File> files = Maps.newHashMap();
        for (Map.Entry<String, Collection<File>> entry : filesWithPotentialNameCollisions.asMap().entrySet()) {
            File[] filesWithSameName = entry.getValue().toArray(new File[0]);
            boolean hasMoreThanOneFileWithSameName = filesWithSameName.length > 1;
            for (int i = 0; i < filesWithSameName.length; i++) {
                File file = filesWithSameName[i];
                String key = entry.getKey();
                if (hasMoreThanOneFileWithSameName) {
                    key += "$" + (i + 1);
                }
                files.put(key, file);
            }
        }

        return files;
    }

    /**
     * Configures the task to sign the archive produced for each of the given tasks (which must be archive tasks).
     */
    public void sign(Task... tasks) {
        for (Task task : tasks) {
            if (!(task instanceof AbstractArchiveTask)) {
                throw new InvalidUserDataException("You cannot sign tasks that are not \'archive\' tasks, such as \'jar\', \'zip\' etc. (you tried to sign " + String.valueOf(task) + ")");
            }

            signTask((AbstractArchiveTask) task);
        }

    }

    private void signTask(final AbstractArchiveTask archiveTask) {
        dependsOn(archiveTask);
        addSignature(new Signature(new Callable<File>() {
            public File call() {
                return archiveTask.getArchivePath();
            }
        }, new Callable<String>() {
            public String call() {
                return archiveTask.getClassifier();
            }
        }, this, this));
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
            configuration.getAllArtifacts().all(
                new Action<PublishArtifact>() {
                    @Override
                    public void execute(PublishArtifact artifact) {
                        if (artifact instanceof Signature) {
                            return;
                        }

                        signArtifact(artifact);
                    }
                });
            configuration.getAllArtifacts().whenObjectRemoved(new Action<PublishArtifact>() {
                @Override
                public void execute(final PublishArtifact publishArtifact) {
                    signatures.remove(Iterables.find(signatures, new Predicate<Signature>() {
                        @Override
                        public boolean apply(Signature input) {
                            return input.getToSignArtifact().equals(publishArtifact);
                        }
                    }));
                }
            });
        }

    }

    private boolean addSignature(Signature signature) {
        return signatures.add(signature);
    }

    /**
     * Changes the signature file representation for the signatures.
     */
    public void signatureType(SignatureType type) {
        this.signatureType = signatureType;
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

        for (Signature signature : signatures) {
            signature.generate();
        }
    }

    /**
     * The signatures generated by this task.
     */
    @Internal
    public DomainObjectSet<Signature> getSignatures() {
        return signatures;
    }

    /**
     * Returns the single signature generated by this task.
     *
     * @return The signature.
     * @throws IllegalStateException if there is not exactly one signature.
     */
    @Internal
    public Signature getSingleSignature() {
        final DomainObjectSet<Signature> signatureSet = getSignatures();
        if (signatureSet.size() == 0) {
            throw new IllegalStateException("Expected %s to contain exactly one signature, however, it contains no signatures.");
        } else if (signatureSet.size() == 1) {
            return signatureSet.iterator().next();
        } else {
            throw new IllegalStateException("Expected %s to contain exactly one signature, however, it contains no " + String.valueOf(signatureSet.size()) + " signatures.");
        }

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
    public FileCollection getFilesToSign() {
        return getFileCollectionFactory().fixed("Task \'" + getPath() + "\' files to sign", Lists.newLinkedList(Iterables.filter(getInputFiles(), Predicates.notNull())));
    }

    /**
     * All of the signature files that will be generated by this operation.
     */
    @Internal
    public FileCollection getSignatureFiles() {
        return getFileCollectionFactory().fixed("Task \'" + getPath() + "\' signature files",
            Lists.newLinkedList(Iterables.filter(Iterables.transform(signatures, SIGNATURE_FILE_FUNCTION), Predicates.notNull())));
    }

    public SignatureType getSignatureType() {
        return signatureType;
    }

    public void setSignatureType(SignatureType signatureType) {
        this.signatureType = signatureType;
    }

    /**
     * Returns the signatory for this signing task.
     * @return the signatory
     */
    public Signatory getSignatory() {
        return signatory;
    }

    public void setSignatory(Signatory signatory) {
        this.signatory = signatory;
    }

    /**
     * Whether or not this task should fail if no signatory or signature type are configured at generation time.
     *
     * <p>Defaults to {@code true}.</p>
     */
    @Input
    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }
}
