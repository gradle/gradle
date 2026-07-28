/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.plugin.devel.tasks

import groovy.transform.SelfType
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.reflect.validation.ValidationMessageChecker
import org.gradle.test.fixtures.file.TestFile

@SelfType(AbstractIntegrationSpec)
trait ValidatePluginsTrait implements CommonPluginValidationTrait, ValidationMessageChecker {

    def setup() {
        enableProblemsApiCheck()
        buildFile """
            apply plugin: "java-gradle-plugin"
        """
    }


    String getIterableSymbol() {
        '.*'
    }

    String getNameSymbolFor(String name) {
        ".<name>"
    }

    String getKeySymbolFor(String name) {
        '.<key>'
    }

    void assertValidationSucceeds() {
        succeeds "validatePlugins"
    }

    void assertValidationFailsWith(int errorCount) {
        fails "validatePlugins"
        failure.assertHasCause("Plugin validation failed with $errorCount problem${errorCount == 1 ? '' : 's'}")
    }

    TestFile source(String path) {
        return file(path)
    }
}
