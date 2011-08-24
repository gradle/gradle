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
import org.gradle.api.artifacts.maven.MavenDeployment
import org.gradle.api.internal.Instantiator
import org.gradle.util.ConfigureUtil

class SigningPluginConvention {
    
    private SigningSettings settings
    
    SigningPluginConvention(SigningSettings settings) {
        this.settings = settings
    }
    
    /**
     * Configures the signing settings of this project.
     *
     * <p>The given closure is use to configure the {@link SigningSettings}. You can configure what information is used to sign
     * and what will be signed.
     *
     * <pre autoTested=''>
     * apply plugin: 'java'
     *
     * signing {
     *   sign configurations.archives
     * }
     * </pre>
     *
     * @param closure The closure to execute.
     */
    SigningSettings signing(Closure closure) {
        ConfigureUtil.configure(closure, settings)
        settings
    }
    
    /**
     * The {@link SigningSettings signing settings} for the project.
     */
    SigningSettings getSigning() {
        settings
    }
    
    /**
     * Digitally signs the publish artifacts, generating signature files alongside them.
     * 
     * <p>The project's default signatory and default signature type from the 
     * {@link SigningSettings signing settings} will be used to generate the signature.
     * The returned {@link SignOperation sign operation} gives access to the created signature files.
     * <p>
     * If there is no configured default signatory available, the sign operation will fail.
     * 
     * @param publishArtifacts The publish artifacts to sign
     * @return The executed {@link SignOperation sign operation}
     */
    SignOperation sign(PublishArtifact... publishArtifacts) {
        doSignOperation {
            sign(*publishArtifacts)
        }
    }

    /**
     * Digitally signs the files, generating signature files alongside them.
     * 
     * <p>The project's default signatory and default signature type from the 
     * {@link SigningSettings signing settings} will be used to generate the signature.
     * The returned {@link SignOperation sign operation} gives access to the created signature files.
     * <p>
     * If there is no configured default signatory available, the sign operation will fail.
     * 
     * @param publishArtifacts The files to sign.
     * @return The executed {@link SignOperation sign operation}.
     */
    SignOperation sign(File... files) {
        doSignOperation {
            sign(*files)
        }
    }
    
    /**
     * Digitally signs the files, generating signature files alongside them.
     * 
     * <p>The project's default signatory and default signature type from the 
     * {@link SigningSettings signing settings} will be used to generate the signature.
     * The returned {@link SignOperation sign operation} gives access to the created signature files.
     * <p>
     * If there is no configured default signatory available, the sign operation will fail.
     * 
     * @param classifier The classifier to assign to the created signature artifacts.
     * @param publishArtifacts The publish artifacts to sign.
     * @return The executed {@link SignOperation sign operation}.
     */
    SignOperation sign(String classifier, File... files) {
        doSignOperation {
            sign(classifier, *files)
        }
    }
    
    /**
     * Creates a new {@link SignOperation sign operation} using the given closure to configure it before executing it.
     * 
     * <p>The project's default signatory and default signature type from the 
     * {@link SigningSettings signing settings} will be used to generate the signature.
     * The returned {@link SignOperation sign operation} gives access to the created signature files.
     * <p>
     * If there is no configured default signatory available (and one is not explicitly specified in this operation's configuration), 
     * the sign operation will fail.
     * 
     * @param closure The configuration of the {@link SignOperation sign operation}.
     * @return The executed {@link SignOperation sign operation}.
     */
    SignOperation sign(Closure closure) {
        doSignOperation(closure)
    }
    
    /**
     * Signs the POM artifact for the given maven deployment.
     * 
     * <p>You can use this method to sign the generated pom when publishing to a maven repository with the maven plugin.
     * </p>
     * <pre autoTested=''>
     * uploadArchives {
     *   repositories {
     *     mavenDeployer {
     *       beforeDeployment { MavenDeployment deployment ->
     *         signPom(deployment)
     *       }
     *     }
     *   }
     * }
     * </pre>
     * <p>You can optionally provide a configuration closure to fine tune the {@link SignOperation sign operation} for the POM.</p>
     * 
     * @param mavenDeployment The deployment to sign the POM of
     * @param closure the configuration of the underlying {@link SignOperation sign operation} for the pom (optional)
     * @return the generated signature artifact
     */
    Signature signPom(MavenDeployment mavenDeployment, Closure closure = null) {
        def signOperation = sign {
            sign(mavenDeployment.pomArtifact)
            ConfigureUtil.configure(closure, delegate)
        }
        
        def pomSignature = signOperation.singleSignature
        
        // Have to alter the “type” of the artifact to match what is published
        // See http://issues.gradle.org/browse/GRADLE-1589
        pomSignature.type = "pom." + pomSignature.signatureType.extension
        
        mavenDeployment.addArtifact(pomSignature)
        pomSignature
    }
    
    protected SignOperation doSignOperation(Closure setup) {
        def operation = settings.project.services.get(Instantiator).newInstance(SignOperation)
        settings.addSignatureSpecConventions(operation)
        operation.configure(setup)
        operation.execute()
        operation
    }
}