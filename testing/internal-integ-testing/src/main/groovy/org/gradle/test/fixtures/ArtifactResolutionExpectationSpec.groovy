/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.test.fixtures

import groovy.transform.CompileStatic

@CompileStatic
trait ArtifactResolutionExpectationSpec<T extends Module> {
    SingleArtifactResolutionResultSpec<T> withModuleMetadataSpec
    SingleArtifactResolutionResultSpec<T> withoutModuleMetadataSpec

    /**
     * Setup expectation when resolving with module metadata
     * @param expectation
     */
    void withModuleMetadata(@DelegatesTo(value = SingleArtifactResolutionResultSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> expectation) {
        expectation.resolveStrategy = Closure.DELEGATE_FIRST
        expectation.delegate = withModuleMetadataSpec
        expectation()
    }

    /**
     * Setup expectation when resolving without module metadata
     * @param expectation
     */
    void withoutModuleMetadata(@DelegatesTo(value = SingleArtifactResolutionResultSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> expectation) {
        expectation.resolveStrategy = Closure.DELEGATE_FIRST
        expectation.delegate = withoutModuleMetadataSpec
        expectation()
    }

    /**
     * Setup expectation when resolving, independently of whether resolving with or without module metadata
     * @param expectation
     */
    void always(@DelegatesTo(value = SingleArtifactResolutionResultSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> expectation) {
        withModuleMetadata(expectation)
        withoutModuleMetadata(expectation)
    }

    /**
     * Short-hand notation for expecting a list of files independently of the fact module metadata is used or not
     * @param fileNames
     */
    void expectFiles(String... fileNames) {
        always {
            expectFiles(fileNames as List<String>)
        }
    }

    void noFiles() {
        always {
            noFiles()
        }
    }

    /**
     * Short-hand notation for expecting a list of files independently of the fact module metadata is used or not
     * @param fileNames
     */
    void expectFiles(List<String> fileNames) {
        always {
            expectFiles(fileNames)
        }
    }

    void createSpecs() {
        withModuleMetadataSpec = new SingleArtifactResolutionResultSpec<T>(module)
        withoutModuleMetadataSpec = new SingleArtifactResolutionResultSpec<T>(module)
    }

    abstract T getModule()

    abstract void validate()
}
