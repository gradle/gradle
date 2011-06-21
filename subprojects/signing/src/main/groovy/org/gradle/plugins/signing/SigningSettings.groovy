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
import org.gradle.util.ConfigureUtil

import org.gradle.plugins.signing.signatory.*

import org.gradle.plugins.signing.type.SignatureType
import org.gradle.plugins.signing.type.handler.SignatureTypeHandler
import org.gradle.plugins.signing.type.handler.DefaultSignatureTypeHandler

class SigningSettings {
    
    static final String DEFAULT_CONFIGURATION_NAME = "signatures"
    
    private Project project
    private Map<String, Signatory> signatories = [:]
    private Configuration configuration
    private SignatureTypeHandler typeHandler
    private boolean required = false
    
    SigningSettings(Project project) {
        this.project = project
        this.configuration = getDefaultConfiguration()
        this.typeHandler = createSignatureTypeHandler()
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
    
    Map<String, Signatory> signatories(Closure block) {
        ConfigureUtil.configure(block, new SignatoriesConfigurer(this))
        signatories
    }
    
    Signatory getSignatory() {
        new SignatoryFactory().createSignatory(project)
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