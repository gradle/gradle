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
import org.gradle.integtests.fixtures.executer.ExecutionFailure

@CompileStatic
class SingleArtifactResolutionResultSpec<T extends Module> {
    private final T module

    SingleArtifactResolutionResultSpec(T module) {
        this.module = module
    }

    boolean expectSuccess = true
    List<String> expectedFileNames = []
    List<Closure<?>> failureExpectations = []

    void expectFiles(String... fileNames) {
        expectFiles(fileNames as List<String>)
    }

    void expectFiles(List<String> fileNames) {
        expectedFileNames = fileNames.sort()
    }

    void noFiles() {
        expectedFileNames = []
    }

    void shouldFail(@DelegatesTo(value = ExecutionFailure, strategy = Closure.DELEGATE_FIRST) Closure<?> onFailure) {
        expectSuccess = false
        if (onFailure == null) {
            throw new IllegalArgumentException("Please provide an explanation for the failure")
        }
        failureExpectations << onFailure
    }

    void noComponentPublished() {
        shouldFail {
            assertHasCause("Could not find ${module.group}:${module.module}:${module.version}")
        }
    }
}
