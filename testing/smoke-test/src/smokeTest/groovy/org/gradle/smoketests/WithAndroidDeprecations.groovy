/*
 * Copyright 2022 the original author or authors.
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

import groovy.transform.SelfType

@SelfType(BaseDeprecations)
trait WithAndroidDeprecations {

    void expectProjectDependencyNotationDeprecationIf(boolean condition) {
        if (condition) {
            expectProjectDependencyNotationDeprecation()
        }
    }

    void expectProjectDependencyNotationDeprecation() {
        runner.expectDeprecationWarning(
            "Using a Project object as a dependency notation has been deprecated. This will fail with an error in Gradle 10. Please use the project(String) method on DependencyHandler or the createProjectDependency(String) method on DependencyFactory instead. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#dependency_project_notation",
            "https://issuetracker.google.com/issues/495889752"
        )
    }

}
