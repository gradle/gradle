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
import org.gradle.plugins.signing.type.handler.SignatureTypeHandler
import org.gradle.plugins.signing.type.handler.DefaultSignatureTypeHandler

import org.gradle.plugins.signing.signatory.pgp.PgpSignatoryProvider

class SigningSettings {
    
    static final String DEFAULT_CONFIGURATION_NAME = "signatures"
    
    private Project project
    private Map<String, Signatory> signatories = [:]
    private Configuration configuration
    private SignatureTypeHandler typeHandler
    private SignatoryProvider signatoryProvider
    private boolean required = true
    
    SigningSettings(Project project) {
        this.project = project
        this.configuration = getDefaultConfiguration()
        this.typeHandler = createSignatureTypeHandler()
        this.signatoryProvider = createSignatoryProvider()
    }
    
    protected Configuration getDefaultConfiguration() {
        def configurations = project.configurations
        def configuration = configurations.findByName(DEFAULT_CONFIGURATION_NAME)
        if (configuration == null) {
            configuration = configurations.add(DEFAULT_CONFIGURATION_NAME)
        }
        configuration
    }
    
    protected SignatureTypeHandler createSignatureTypeHandler() {
        new DefaultSignatureTypeHandler()
    }
    
    protected SignatoryProvider createSignatoryProvider() {
        new PgpSignatoryProvider()
    }
    
    Map<String, Signatory> signatories(Closure block) {
        signatoryProvider.configure(this, block)
        signatories
    }
    
    /**
     * Registers the given signatory with the settings.
     * 
     * @param signatory The signatory to register
     * @throws org.grails.api.InvalidUserDataException if there is already a signatory registered with this name
     */
    void addSignatory(Signatory signatory) {
        if (hasSignatory(signatory.name)) {
            throw new InvalidUserDataException("cannot add signatory with name '$signatory.name' as there is already a signatory registered with this name")
        }
        
        signatories[signatory.name] = signatory
    }
    
    /**
     * <p>Checks whether there is already a signatory registered with the <strong>same name</strong> as the argument.</p>
     * 
     * <p>Note that the name is used rather than {@code equals()}</p>
     */
    boolean hasSignatory(Signatory signatory) {
        hasSignatory(signatory.name)
    }
    
    /**
     * Checks whether there is a signatory registered with this name.
     */
    boolean hasSignatory(String name) {
        signatories.containsKey(name)
    }
    
    Signatory getSignatory() {
        signatoryProvider.getDefaultSignatory(project)
    }
    
    SignatureType getType() {
        typeHandler.defaultType
    }
    
    Configuration getConfiguration() {
        configuration
    }
    
    boolean getRequired() {
        required
    }
    
    void setRequired(boolean required) {
        this.required = required
    }
    
    void required(boolean required) {
        setRequired(required)
    }
    
    void addSignatureSpecConventions(SignatureSpec spec) {
        if (!(spec instanceof IConventionAware)) {
            throw new InvalidUserDataException("Cannot add conventions to signature spec '$spec' as it is not convention aware")
        }
        
        spec.conventionMapping.map('signatory') { getSignatory() }
        spec.conventionMapping.map('type') { getType() }
    }
    
    Sign sign(Task task) {
        sign([task] as Task[]).first()
    }
    
    Collection<Sign> sign(Task[] tasksToSign) {
        tasksToSign.collect { taskToSign ->
            def signTask = project.task("sign${taskToSign.name.capitalize()}", type: Sign) {
                sign taskToSign
            }
            configuration.addArtifact(signTask.singleArtifact)
            signTask
        }
    }
    
    Sign sign(PublishArtifact artifact) {
        def signTask = project.task("sign${artifact.name.capitalize()}", type: Sign) {
            delegate.sign artifact
        }
        configuration.addArtifact(signTask.singleArtifact)
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
            signTask.artifacts.each { getConfiguration().addArtifact(it) }
            signTask
        }
    }
}