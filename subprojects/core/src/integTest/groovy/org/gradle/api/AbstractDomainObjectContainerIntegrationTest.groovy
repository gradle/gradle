/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

abstract class AbstractDomainObjectContainerIntegrationTest extends AbstractIntegrationSpec {
    abstract String makeContainer()
    abstract String getContainerStringRepresentation()

    String disallowMutationMessage(String assertingMethod) {
        return "$assertingMethod on ${targetStringRepresentation(assertingMethod)} cannot be executed in the current context."
    }

    private String targetStringRepresentation(String method) {
        if (method.startsWith("Project")) {
            return "root project 'root'"
        } else if (method.startsWith("Gradle")) {
            return "build 'root'"
        } else {
            return containerStringRepresentation
        }
    }

    def setup() {
        settingsFile << """
            rootProject.name = 'root'
            gradle.projectsLoaded {
                it.rootProject {
                    ext.testContainer = ${makeContainer()}
                    ext.toBeRealized = testContainer.register('toBeRealized')
                    ext.unrealized = testContainer.register('unrealized')
                    ext.realized = testContainer.register('realized')
                    realized.get()
                }
            }
        """
    }

    Map<String, String> getQueryMethods() {
        [
            "${containerType}#getByName(String)":    "testContainer.getByName('unrealized')",
            "${containerType}#named(String)":        "testContainer.named('unrealized')",
            "${containerType}#named(String, Class)": "testContainer.named('unrealized', testContainer.type)",
            "${containerType}#findAll(Closure)":     "testContainer.findAll { it.name == 'unrealized' }",
            "${containerType}#findByName(String)":   "testContainer.findByName('unrealized')",
            "${containerType}#TaskProvider.get()":   "unrealized.get()",
            "${containerType}#iterator()":           "for (def element : testContainer) { println element.name }",
        ]
    }

    Map<String, String> getMutationMethods() {
        [
            "${containerType}#create(String)": "testContainer.create('mutate')",
            "${containerType}#register(String)": "testContainer.register('mutate')",
            "${containerType}#getByName(String, Action)": "testContainer.getByName('realized') {}",
            "${containerType}#configureEach(Action)": "testContainer.configureEach {}",
            "${containerType}#NamedDomainObjectProvider.configure(Action)": "toBeRealized.configure {}",
            "${containerType}#named(String, Action)": "testContainer.named('realized') {}",
            "${containerType}#whenObjectAdded(Action)": "testContainer.whenObjectAdded {}",
            "${containerType}#withType(Class, Action)": "testContainer.withType(testContainer.type) {}",
            "${containerType}#all(Action)": "testContainer.all {}",
            "Project#afterEvaluate(Closure)": "afterEvaluate {}",
            "Project#beforeEvaluate(Closure)": "beforeEvaluate {}",
            "Gradle#beforeProject(Closure)": "gradle.beforeProject {}",
            "Gradle#afterProject(Closure)": "gradle.afterProject {}",
            "Gradle#projectsLoaded(Closure)": "gradle.projectsLoaded {}",
            "Gradle#projectsEvaluated(Closure)": "gradle.projectsEvaluated {}",
        ]
    }
}
