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

import org.gradle.util.ConfigureUtil
import org.gradle.api.internal.ClassGenerator

import org.gradle.plugins.signing.signatory.*

class SigningConvention {
    private SigningSettings settings
    
    SigningConvention(SigningSettings settings) {
        this.settings = settings
    }
    
    SigningSettings signing(Closure block) {
        ConfigureUtil.configure(block, settings)
        settings
    }
    
    SigningSettings getSigning() {
        settings
    }
    
    SignOperation sign(PublishArtifact... toSign) {
        doSignOperation {
            sign(*toSign)
        }
    }
    
    SignOperation sign(File... toSign) {
        doSignOperation {
            sign(*toSign)
        }
    }
    
    SignOperation sign(String classifier, File... toSign) {
        doSignOperation {
            sign(classifier, *toSign)
        }
    }
    
    SignOperation sign(Closure closure) {
        doSignOperation(closure)
    }
    
    protected SignOperation doSignOperation(Closure setup) {
        def operation = settings.project.services.get(ClassGenerator).newInstance(SignOperation)
        settings.addSignatureSpecConventions(operation)
        ConfigureUtil.configure(setup, operation)
        operation.execute()
        operation
    }
}