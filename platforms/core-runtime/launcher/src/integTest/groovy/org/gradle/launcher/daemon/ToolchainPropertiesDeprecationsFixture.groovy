/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.launcher.daemon

trait ToolchainPropertiesDeprecationsFixture {

    void expectToolchainPropertyDeprecationFor(String propertyName, String value) {
        executer.expectDocumentedDeprecationWarning(
            "Specifying '${propertyName}' as a project property on the command line has been deprecated." +
                " This is scheduled to be removed in Gradle 10." +
                " Instead, specify it as a Gradle property: '-D${propertyName}=${value}'." +
                " Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#toolchain-project-properties"
        )
    }
}
