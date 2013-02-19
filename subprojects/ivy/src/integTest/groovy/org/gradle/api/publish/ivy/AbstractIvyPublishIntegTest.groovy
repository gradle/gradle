/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.publish.ivy

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.ivy.IvyFileModule

class AbstractIvyPublishIntegTest extends AbstractIntegrationSpec {

    protected def resolveArtifacts(IvyFileModule module) {
        doResolveArtifacts("group: '${module.organisation}', name: '${module.module}', version: '${module.revision}'")
    }

    protected def resolveArtifacts(IvyFileModule module, def configuration) {
        doResolveArtifacts("group: '${module.organisation}', name: '${module.module}', version: '${module.revision}', configuration: '${configuration}'")
    }

    private def doResolveArtifacts(def dependency) {
        // Replace the existing buildfile with one for resolving the published module
        // TODO:DAZ Use a separate directory for resolving
        settingsFile.text = "rootProject.name = 'resolve'"
        buildFile.text = """
            configurations {
                resolve
            }
            repositories {
                ivy { url "${ivyRepo.uri}" }
                mavenCentral()
            }
            dependencies {
                resolve $dependency
            }

            task resolveArtifacts(type: Sync) {
                from configurations.resolve
                into "artifacts"
            }

"""

        run "resolveArtifacts"
        def artifactsList = file("artifacts").exists() ? file("artifacts").list() : []
        return artifactsList.sort()
    }

}
