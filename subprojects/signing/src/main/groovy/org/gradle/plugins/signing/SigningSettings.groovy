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
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.PublishArtifact
import org.gradle.api.InvalidUserDataException
import org.gradle.api.internal.IConventionAware

import org.gradle.plugins.signing.signatory.*

import org.gradle.plugins.signing.type.SignatureType
import org.gradle.plugins.signing.type.SignatureTypeProvider
import org.gradle.plugins.signing.type.DefaultSignatureTypeProvider

import org.gradle.plugins.signing.signatory.pgp.PgpSignatoryProvider

/**
 * The global signing configuration for a project.
 */
class SigningSettings {
    
    /**
     * The name of the configuration that all signature artifacts will be placed into (“signatures”)
     */
    static final String DEFAULT_CONFIGURATION_NAME = "signatures"
    
    /**
     * The project that the settings are for
     */
    final Project project
    
    /**
     * The configuration that signature artifacts will be placed into.
     * 
     * <p>Changing this will not affect any signing already configured.</p>
     */
    Configuration configuration
    
    /**
     * Whether or not this task should fail if no signatory or signature type are configured at generation time.
     * 
     * <p>Defaults to {@code true}.</p>
     */
    boolean required = true
    
    /**
     * The provider of signature types.
     */
    SignatureTypeProvider signatureTypes
    
    /**
     * The provider of signatories.
     */
    SignatoryProvider signatories
    
    /**
     * Configures the signing settings for the given project.
     */
    SigningSettings(Project project) {
        this.project = project
        this.configuration = getDefaultConfiguration()
        this.signatureTypes = createSignatureTypeProvider()
        this.signatories = createSignatoryProvider()
    }
    
    /**
     * Provides the configuration that signature artifacts are added to. Called once during construction.
     */
    protected Configuration getDefaultConfiguration() {
        def configurations = project.configurations
        def configuration = configurations.findByName(DEFAULT_CONFIGURATION_NAME)
        if (configuration == null) {
            configuration = configurations.add(DEFAULT_CONFIGURATION_NAME)
        }
        configuration
    }
    
    /**
     * Provides the signature type provider. Called once during construction.
     */
    protected SignatureTypeProvider createSignatureTypeProvider() {
        new DefaultSignatureTypeProvider()
    }
    
    /**
     * Provides the signatory provider. Called once during construction.
     */
    protected SignatoryProvider createSignatoryProvider() {
        new PgpSignatoryProvider()
    }
    
    /**
     * Configures the signatory provider (delegating to its {@link SignatoryProvider#configure(SigningSettings,Closure) configure method}).
     * 
     * @param closure the signatory provider configuration DSL
     * @return the configured signatory provider
     */
    SignatoryProvider signatories(Closure closure) {
        signatories.configure(this, closure)
        signatories
    }
    
    /**
     * The signatory that will be used for signing when an explicit signatory has not been specified.
     * 
     * <p>Delegates to the signatory provider's default signatory.</p>
     */
    Signatory getSignatory() {
        signatories.getDefaultSignatory(project)
    }
    
    /**
     * The signature type that will be used for signing files when an explicit signature type has not been specified.
     * 
     * <p>Delegates to the signature type provider's default type.</p>
     */
    SignatureType getSignatureType() {
        signatureTypes.defaultType
    }
    
    /**
     * The configuration that signature artifacts are added to.
     */
    Configuration getConfiguration() {
        configuration
    }
    
    /**
     * Whether or not signing tasks should fail if no signatory or signature type are configured at generation time.
     * 
     * <p>Defaults to {@code true}.</p>
     */
    boolean getRequired() {
        required
    }
    
    /**
     * Sets whether or not signing tasks should fail if no signatory or signature type are configured at generation time.
     */
    void setRequired(boolean required) {
        this.required = required
    }
    
    /**
     * Sets whether or not signing tasks should fail if no signatory or signature type are configured at generation time.
     */
    void required(boolean required) {
        setRequired(required)
    }
    
    /**
     * Adds conventions to the given spec, using this settings object's default signatory and signature type as the
     * default signatory and signature type for the spec.
     */
    void addSignatureSpecConventions(SignatureSpec spec) {
        if (!(spec instanceof IConventionAware)) {
            throw new InvalidUserDataException("Cannot add conventions to signature spec '$spec' as it is not convention aware")
        }
        
        spec.conventionMapping.map('signatory') { getSignatory() }
        spec.conventionMapping.map('signatureType') { getSignatureType() }
    }
    
    /**
     * Creates a signing task that depends on and signs the “archive” produced by the given task.
     * 
     * <p>The created task will be named “sign«input task name capitalized»”. That is, given a task with the name “jar”
     * the created task will be named “signJar”.
     * <p>
     * If the task is not an {@link org.gradle.api.tasks.bundling.AbstractArchiveTask}, an 
     * {@link org.gradle.api.InvalidUserDataException} will be thrown.</p>
     * <p>
     * The signature artifact for the created task is added to the {@link #getConfiguration() for this settings object}.
     * 
     * @param task The task whose archive is to be signed
     * @return the created task.
     */
    Sign sign(Task task) {
        sign([task] as Task[]).first()
    }
    
    Collection<Sign> sign(Task[] tasksToSign) {
        tasksToSign.collect { taskToSign ->
            def signTask = project.task("sign${taskToSign.name.capitalize()}", type: Sign) {
                sign taskToSign
            }
            addSignaturesToConfiguration(signTask, getConfiguration())
            signTask
        }
    }
    
    Sign sign(PublishArtifact artifact) {
        def signTask = project.task("sign${artifact.name.capitalize()}", type: Sign) {
            delegate.sign artifact
        }
        addSignaturesToConfiguration(signTask, getConfiguration())
        signTask
    }
    
    Sign sign(Configuration configuration) {
        sign([configuration] as Configuration[]).first()
    }
    
    Collection<Sign> sign(Configuration[] configurations) {
        configurations.collect { configuration ->
            def signTask = project.task("sign${configuration.name.capitalize()}", type: Sign) {
                sign configuration
            }
            addSignaturesToConfiguration(signTask, getConfiguration())
            signTask
        }
    }
    
    private addSignaturesToConfiguration(Sign task, Configuration configuration) {
        task.signatures.all { configuration.artifacts.add(it) }
        task.signatures.whenObjectRemoved { configuration.artifacts.remove(it) }
    }
}