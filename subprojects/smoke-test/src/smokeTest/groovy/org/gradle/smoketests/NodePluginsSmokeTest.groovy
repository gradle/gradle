/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.smoketests

import org.gradle.internal.reflect.validation.ValidationMessageChecker

import static org.gradle.api.problems.interfaces.Severity.ERROR

class NodePluginsSmokeTest extends AbstractPluginValidatingSmokeTest implements ValidationMessageChecker {
    @Override
    Map<String, Versions> getPluginsToValidate() {
        [
            // Grunt and Gulp plugins are not properly published on the plugin portal
            //'com.moowork.grunt': TestedVersions.node,
            //'com.moowork.gulp': TestedVersions.node,
            'com.moowork.node': TestedVersions.node,
            'com.github.node-gradle.node': TestedVersions.newNode
        ]
    }

    @Override
    void configureValidation(String testedPluginId, String version) {
        validatePlugins {
            if (testedPluginId == 'com.moowork.node') {
                onPlugin('com.moowork.node') {
                    failsWith([
                        (missingAnnotationMessage { type('com.moowork.gradle.node.npm.NpmSetupTask').property('args').missingInputOrOutput().includeLink() }): ERROR,
                        (methodShouldNotBeAnnotatedMessage {type('com.moowork.gradle.node.npm.NpmSetupTask').kind('setter').method('setArgs').annotation('Internal').includeLink()}): ERROR,
                        (missingAnnotationMessage { type('com.moowork.gradle.node.yarn.YarnSetupTask').property('args').missingInputOrOutput().includeLink() }): ERROR,
                    ])
                }
            } else {
                alwaysPasses()
            }
        }
    }
}
