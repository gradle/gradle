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
import groovy.lang.DelegatesTo;
import org.gradle.api.Action;
import org.gradle.api.DomainObjectCollection;
import org.gradle.api.Incubating;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.internal.ConventionMapping;
import org.gradle.api.internal.IConventionAware;
import org.gradle.api.internal.artifacts.configurations.ConfigurationRolesForMigration;
import org.gradle.api.internal.artifacts.configurations.RoleBasedConfigurationContainerInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.publish.Publication;
import org.gradle.api.publish.PublicationArtifact;
import org.gradle.api.publish.internal.PublicationInternal;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.internal.Cast;
import org.gradle.plugins.signing.internal.SignOperationInternal;
import org.gradle.plugins.signing.signatory.Signatory;
import org.gradle.plugins.signing.signatory.SignatoryProvider;
import org.gradle.plugins.signing.signatory.internal.gnupg.GnupgSignatoryProvider;
import org.gradle.plugins.signing.signatory.internal.pgp.InMemoryPgpSignatoryProvider;
import org.gradle.plugins.signing.signatory.pgp.PgpSignatoryProvider;
import org.gradle.plugins.signing.type.DefaultSignatureTypeProvider;
import org.gradle.plugins.signing.type.SignatureType;
import org.gradle.plugins.signing.type.SignatureTypeProvider;
import org.gradle.util.internal.DeferredUtil;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import static org.codehaus.groovy.runtime.StringGroovyMethods.capitalize;
import static org.codehaus.groovy.runtime.typehandling.DefaultTypeTransformation.castToBoolean;

/**
 * The global signing configuration for a project.
 */
public abstract class SigningExtension {

    /**
     * The name of the configuration that all signature artifacts will be placed into ("signatures")
     */
    public static final String DEFAULT_CONFIGURATION_NAME = "signatures";

    /**
     * The project that the settings are for
     */
    private final Project project;

    /**
     * The configuration that signature artifacts will be placed into.
     *
     * <p>Changing this will not affect any signing already configured.</p>
     */
    private Configuration configuration;

    private Object required = true;

    /**
     * The provider of signature types.
     */
    private SignatureTypeProvider signatureTypes;

    /**
     * The provider of signatories.
     */
    private SignatoryProvider<?> signatories;

    /**
     * Configures the signing settings for the given project.
     */
    public SigningExtension(Project project) {
        this.project = project;
        this.configuration = getDefaultConfiguration();
        this.signatureTypes = createSignatureTypeProvider();
        this.signatories = createSignatoryProvider();
        project.getTasks().withType(Sign.class, this::addSignatureSpecConventions);
    }

    public final Project getProject() {
        return project;
    }

    /**
     * Whether this task should fail if no signatory or signature type are configured at generation time.
     *
     * @since 4.0
     */
    public void setRequired(boolean required) {
        this.required = required;
    }

    /**
     * Whether this task should fail if no signatory or signature type are configured at generation time.
     *
     * If {@code required} is a {@link Callable}, it will be stored and "called" on demand (i.e. when {@link #isRequired()} is called) and the return value will be interpreting according to the Groovy
     * Truth. For example:
     *
     * <pre>
     * signing {
     *   required = { gradle.taskGraph.hasTask("publish") }
     * }
     * </pre>
     *
     * Because the task graph is not known until Gradle starts executing, we must use defer the decision. We can do this via using a {@link Closure} (which is a {@link Callable}).
     *
     * For any other type, the value will be stored and evaluated on demand according to the Groovy Truth.
     *
     * <pre>
     * signing {
     *   required = false
     * }
     * </pre>
     */
    public void setRequired(Object required) {
        this.required = required;
    }

    /**
     * Whether this task should fail if no signatory or signature type are configured at generation time.
     *
     * <p>Defaults to {@code true}.</p>
     *
     * @see #setRequired(Object)
     */
    public boolean isRequired() {
        return castToBoolean(force(required));
    }

    /**
     * Provides the configuration that signature artifacts are added to. Called once during construction.
     */
    protected Configuration getDefaultConfiguration() {
        final RoleBasedConfigurationContainerInternal configurations = ((ProjectInternal) project).getConfigurations();
        final Configuration configuration = configurations.findByName(DEFAULT_CONFIGURATION_NAME);
        return configuration != null
            ? configuration
            : configurations.migratingUnlocked(DEFAULT_CONFIGURATION_NAME, ConfigurationRolesForMigration.LEGACY_TO_CONSUMABLE).get();
    }

    /**
     * Provides the signature type provider. Called once during construction.
     */
    protected SignatureTypeProvider createSignatureTypeProvider() {
        return new DefaultSignatureTypeProvider();
    }

    /**
     * Provides the signatory provider. Called once during construction.
     */
    protected SignatoryProvider<?> createSignatoryProvider() {
        return new PgpSignatoryProvider();
    }

    /**
     * Configures the signatory provider (delegating to its {@link SignatoryProvider#configure(SigningExtension, Closure) configure method}).
     *
     * @param closure the signatory provider configuration DSL
     * @return the configured signatory provider
     */
    @SuppressWarnings("unused")
    public SignatoryProvider<?> signatories(Closure<?> closure) {
        signatories.configure(this, closure);
        return signatories;
    }

    /**
     * The signatory that will be used for signing when an explicit signatory has not been specified.
     *
     * <p>Delegates to the signatory provider's default signatory.</p>
     */
    public Signatory getSignatory() {
        return signatories.getDefaultSignatory(project);
    }

    /**
     * The signature type that will be used for signing files when an explicit signature type has not been specified.
     *
     * <p>Delegates to the signature type provider's default type.</p>
     */
    public SignatureType getSignatureType() {
        return signatureTypes.getDefaultType();
    }

    @SuppressWarnings("unused")
    public void setSignatureTypes(SignatureTypeProvider signatureTypes) {
        this.signatureTypes = signatureTypes;
    }

    @SuppressWarnings("unused")
    public SignatureTypeProvider getSignatureTypes() {
        return signatureTypes;
    }

    public void setSignatories(SignatoryProvider<?> signatories) {
        this.signatories = signatories;
    }

    public void setConfiguration(Configuration configuration) {
        this.configuration = configuration;
    }

    /**
     * Use GnuPG agent to perform signing work.
     *
     * @since 4.5
     */
    @SuppressWarnings("unused")
    public void useGpgCmd() {
        setSignatories(new GnupgSignatoryProvider());
    }

    /**
     * Use the supplied ascii-armored in-memory PGP secret key and password
     * instead of reading it from a keyring.
     *
     * <pre><code>
     * signing {
     *     def secretKey = findProperty("mySigningKey")
     *     def password = findProperty("mySigningPassword")
     *     useInMemoryPgpKeys(secretKey, password)
     * }
     * </code></pre>
     *
     * @since 5.4
     */
    @SuppressWarnings("unused")
    public void useInMemoryPgpKeys(@Nullable String defaultSecretKey, @Nullable String defaultPassword) {
        setSignatories(new InMemoryPgpSignatoryProvider(defaultSecretKey, defaultPassword));
    }

    /**
     * Use the supplied ascii-armored in-memory PGP secret key and password
     * instead of reading it from a keyring.
     * In case a signing subkey is provided, keyId must be provided as well.
     *
     * <pre><code>
     * signing {
     *     def keyId = findProperty("keyId")
     *     def secretKey = findProperty("mySigningKey")
     *     def password = findProperty("mySigningPassword")
     *     useInMemoryPgpKeys(keyId, secretKey, password)
     * }
     * </code></pre>
     *
     * @since 6.0
     */
    @SuppressWarnings("unused")
    public void useInMemoryPgpKeys(@Nullable String defaultKeyId, @Nullable String defaultSecretKey, @Nullable String defaultPassword) {
        setSignatories(new InMemoryPgpSignatoryProvider(defaultKeyId, defaultSecretKey, defaultPassword));
    }

    /**
     * The configuration that signature artifacts are added to.
     */
    public Configuration getConfiguration() {
        return configuration;
    }

    /**
     * Adds conventions to the given spec, using this settings object's default signatory and signature type as the default signatory and signature type for the spec.
     */
    protected void addSignatureSpecConventions(SignatureSpec spec) {
        if (!(spec instanceof IConventionAware)) {
            throw new InvalidUserDataException("Cannot add conventions to signature spec '" + spec + "' as it is not convention aware");
        }

        ConventionMapping conventionMapping = ((IConventionAware) spec).getConventionMapping();
        conventionMapping.map("signatory", this::getSignatory);
        conventionMapping.map("signatureType", this::getSignatureType);
        conventionMapping.map("required", this::isRequired);
    }

    /**
     * Creates signing tasks that depend on and sign the "archive" produced by the given tasks.
     *
     * <p>The created tasks will be named "sign<i>&lt;input task name capitalized&gt;</i>". That is, given a task with the name "jar" the created task will be named "signJar". <p> If the task is not
     * an {@link org.gradle.api.tasks.bundling.AbstractArchiveTask}, an {@link InvalidUserDataException} will be thrown.</p> <p> The signature artifact for the created task is added to the {@link
     * #getConfiguration() for this settings object}.
     *
     * @param tasks The tasks whose archives are to be signed
     * @return the created tasks.
     */
    public List<Sign> sign(Task... tasks) {
        final List<Sign> result = new ArrayList<>(tasks.length);
        for (final Task taskToSign : tasks) {
            result.add(
                createSignTaskFor(taskToSign.getName(), task -> {
                    task.setDescription("Signs the archive produced by the '" + taskToSign.getName() + "' task.");
                    task.sign(taskToSign);
                })
            );
        }
        return result;
    }

    /**
     * Creates signing tasks that sign {@link Configuration#getAllArtifacts() all artifacts} of the given configurations.
     *
     * <p>The created tasks will be named "sign<i>&lt;configuration name capitalized&gt;</i>". That is, given a configuration with the name "conf" the created task will be named "signConf".
     *
     * The signature artifacts for the created tasks are added to the {@link #getConfiguration() configuration} for this settings object.
     *
     * @param configurations The configurations whose archives are to be signed
     * @return the created tasks.
     */
    public List<Sign> sign(Configuration... configurations) {
        final List<Sign> result = new ArrayList<>(configurations.length);
        for (final Configuration configurationToSign : configurations) {
            result.add(
                createSignTaskFor(configurationToSign.getName(), task -> {
                    task.setDescription("Signs all artifacts in the '" + configurationToSign.getName() + "' configuration.");
                    task.sign(configurationToSign);
                })
            );
        }
        return result;
    }

    /**
     * Creates signing tasks that sign all publishable artifacts of the given publications.
     *
     * <p>The created tasks will be named "sign<i>&lt;publication name capitalized&gt;</i>Publication".
     * That is, given a publication with the name "mavenJava" the created task will be named "signMavenJavaPublication".
     *
     * The signature artifacts for the created tasks are added to the publishable artifacts of the given publications.
     *
     * @param publications The publications whose artifacts are to be signed
     * @return the created tasks.
     * @since 4.8
     */
    public List<Sign> sign(Publication... publications) {
        final List<Sign> result = new ArrayList<>(publications.length);
        for (final Publication publication : publications) {
            result.add(createSignTaskFor((PublicationInternal<?>) publication));
        }
        return result;
    }

    /**
     * Creates signing tasks that sign all publishable artifacts of the given publication collection.
     *
     * <p>The created tasks will be named "sign<i>&lt;publication name capitalized&gt;</i>Publication".
     * That is, given a publication with the name "mavenJava" the created task will be named "signMavenJavaPublication".
     *
     * The signature artifacts for the created tasks are added to the publishable artifacts of the given publications.
     *
     * @param publications The collection of publications whose artifacts are to be signed
     * @return the created tasks.
     * @since 4.8
     */
    public List<Sign> sign(DomainObjectCollection<Publication> publications) {
        final List<Sign> result = new ArrayList<>();
        publications.all(publication -> result.add(createSignTaskFor((PublicationInternal<?>) publication)));
        publications.whenObjectRemoved(publication -> {
            final TaskContainer tasks = project.getTasks();
            final Task task = tasks.getByName(determineSignTaskNameForPublication(publication));
            task.setEnabled(false);
            result.remove(task);
        });
        return result;
    }

    private <T extends PublicationArtifact> Sign createSignTaskFor(final PublicationInternal<T> publicationToSign) {
        final String signTaskName = determineSignTaskNameForPublication(publicationToSign);
        if (project.getTasks().getNames().contains(signTaskName)) {
            return project.getTasks().named(signTaskName, Sign.class).get();
        }
        final Sign signTask = project.getTasks().create(signTaskName, Sign.class, task -> {
            task.setDescription("Signs all artifacts in the '" + publicationToSign.getName() + "' publication.");
            task.sign(publicationToSign);
        });
        final Map<Signature, T> artifacts = new HashMap<>();
        signTask.getSignatures().all(signature -> {
            final T artifact = publicationToSign.addDerivedArtifact(
                Cast.uncheckedNonnullCast(signature.getSource()),
                new DefaultDerivedArtifactFile(signature, signTask)
            );
            artifact.builtBy(signTask);
            artifacts.put(signature, artifact);
        });
        signTask.getSignatures().whenObjectRemoved(signature -> {
            final T artifact = artifacts.remove(signature);
            publicationToSign.removeDerivedArtifact(artifact);
        });
        return signTask;
    }

    private String determineSignTaskNameForPublication(Publication publication) {
        return "sign" + capitalize(publication.getName()) + "Publication";
    }

    private Sign createSignTaskFor(CharSequence name, Action<Sign> taskConfiguration) {
        final String signTaskName = "sign" + capitalize(name);
        if (project.getTasks().getNames().contains(signTaskName)) {
            return project.getTasks().named(signTaskName, Sign.class).get();
        }
        final Sign signTask = project.getTasks().create(signTaskName, Sign.class, taskConfiguration);
        addSignaturesToConfiguration(signTask, getConfiguration());
        return signTask;
    }

    protected Object addSignaturesToConfiguration(Sign task, final Configuration configuration) {
        task.getSignatures().all(sig -> configuration.getArtifacts().add(sig));
        return task.getSignatures().whenObjectRemoved(sig -> configuration.getArtifacts().remove(sig));
    }

    /**
     * Digitally signs the publish artifacts, generating signature files alongside them.
     *
     * <p>The project's default signatory and default signature type from the {@link SigningExtension signing settings} will be used to generate the signature.
     * The returned {@link SignOperation sign operation} gives access to the created signature files.
     * <p>If there is no configured default signatory available, the sign operation will fail.
     *
     * @param publishArtifacts The publish artifacts to sign
     * @return The executed {@link SignOperation sign operation}
     */
    public SignOperation sign(final PublishArtifact... publishArtifacts) {
        return doSignOperation(operation -> operation.sign(publishArtifacts));
    }

    /**
     * Digitally signs the files, generating signature files alongside them.
     *
     * <p>The project's default signatory and default signature type from the {@link SigningExtension signing settings} will be used to generate the signature.
     * The returned {@link SignOperation sign operation} gives access to the created signature files.
     * <p>If there is no configured default signatory available, the sign operation will fail.
     *
     * @param files The files to sign.
     * @return The executed {@link SignOperation sign operation}.
     */
    public SignOperation sign(final File... files) {
        return doSignOperation(operation -> operation.sign(files));
    }

    /**
     * Digitally signs the files, generating signature files alongside them.
     *
     * <p>The project's default signatory and default signature type from the {@link SigningExtension signing settings} will be used to generate the signature.
     * The returned {@link SignOperation sign
     * operation} gives access to the created signature files.
     * <p>If there is no configured default signatory available, the sign operation will fail.
     *
     * @param classifier The classifier to assign to the created signature artifacts.
     * @param files The publish artifacts to sign.
     * @return The executed {@link SignOperation sign operation}.
     */
    public SignOperation sign(final String classifier, final File... files) {
        return doSignOperation(operation -> operation.sign(classifier, files));
    }

    /**
     * Creates a new {@link SignOperation sign operation} using the given closure to configure it before executing it.
     *
     * <p>The project's default signatory and default signature type from the {@link SigningExtension signing settings} will be used to generate the signature.
     * The returned {@link SignOperation sign
     * operation} gives access to the created signature files.
     * <p>If there is no configured default signatory available (and one is not explicitly specified in this operation's configuration), the sign operation will fail.
     *
     * @param closure The configuration of the {@link SignOperation sign operation}.
     * @return The executed {@link SignOperation sign operation}.
     */
    public SignOperation sign(@DelegatesTo(SignOperation.class) Closure<?> closure) {
        return doSignOperation(closure);
    }

    /**
     * Creates a new {@link SignOperation sign operation} using the given action to configure it before executing it.
     *
     * <p>The project's default signatory and default signature type from the {@link SigningExtension signing settings} will be used to generate the signature.
     * The returned {@link SignOperation sign
     * operation} gives access to the created signature files.
     * <p>If there is no configured default signatory available (and one is not explicitly specified in this operation's configuration), the sign operation will fail.
     *
     * @param setup The configuration action of the {@link SignOperation sign operation}.
     * @return The executed {@link SignOperation sign operation}.
     * @since 7.5
     */
    @Incubating
    public SignOperation sign(Action<SignOperation> setup) {
        return doSignOperation(setup);
    }

    protected SignOperation doSignOperation(@DelegatesTo(SignOperation.class) final Closure<?> setup) {
        return doSignOperation(operation -> operation.configure(setup));
    }

    protected SignOperation doSignOperation(Action<SignOperation> setup) {
        final SignOperation operation = objectFactory().newInstance(SignOperationInternal.class);
        addSignatureSpecConventions(operation);
        setup.execute(operation);
        operation.execute();
        return operation;
    }

    private ObjectFactory objectFactory() {
        return project.getObjects();
    }

    public SignatoryProvider<?> getSignatories() {
        return signatories;
    }

    private Object force(Object maybeCallable) {
        return DeferredUtil.unpack(maybeCallable);
    }

    private static class DefaultDerivedArtifactFile implements PublicationInternal.DerivedArtifact {
        private final Signature signature;
        private final Sign signTask;

        public DefaultDerivedArtifactFile(Signature signature, Sign signTask) {
            this.signature = signature;
            this.signTask = signTask;
        }

        @Override
        public File create() {
            return signature.getFile();
        }

        @Override
        public boolean shouldBePublished() {
            return signTask.isEnabled()
                && signTask.getOnlyIf().isSatisfiedBy(signTask);
        }
    }
}
