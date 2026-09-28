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
package org.gradle.buildinit.plugins

import org.gradle.api.artifacts.ArtifactRepositoryContainer
import org.gradle.integtests.fixtures.RepoScriptBlockUtil
import org.gradle.integtests.fixtures.WellBehavedPluginTest

class BuildInitPluginGoodBehaviourIntegrationTest extends WellBehavedPluginTest {

    def setup() {
        // Applying the build-init plugin registers the init task, and BuildInitPlugin configures
        // the build converter's classpath at that point, so the Maven conversion libraries are
        // resolved through a detached resolver that no init script can reach. This class does not
        // extend AbstractInitIntegrationSpec, so it needs the Maven settings mirror of its own.
        def mirrorUrl = RepoScriptBlockUtil.mavenCentralMirrorUrl
        if (RepoScriptBlockUtil.mirrorEnabled && mirrorUrl != ArtifactRepositoryContainer.MAVEN_CENTRAL_URL) {
            using m2
            m2.withCentralMirror(mirrorUrl)
            executer.beforeExecute {
                it.withArgument("-Dorg.gradle.mirror.maven.settings=true")
            }
        }
    }

    @Override
    def getMainTask() {
        return ["init", "--overwrite"]
    }
}
