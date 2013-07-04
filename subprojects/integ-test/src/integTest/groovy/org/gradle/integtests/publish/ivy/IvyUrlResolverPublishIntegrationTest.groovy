/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.integtests.publish.ivy

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.ProgressLoggingFixture
import org.gradle.test.fixtures.ivy.IvyHttpModule
import org.gradle.test.fixtures.ivy.IvyHttpRepository
import org.gradle.test.fixtures.server.http.HttpServer
import org.junit.Rule

public class IvyUrlResolverPublishIntegrationTest extends AbstractIntegrationSpec {
    @Rule ProgressLoggingFixture progressLogging = new ProgressLoggingFixture(executer, temporaryFolder)
    @Rule HttpServer server = new HttpServer()

    private IvyHttpModule module
    private IvyHttpRepository ivyHttpRepo

    def setup() {
        ivyHttpRepo = new IvyHttpRepository(server, ivyRepo)
        module = ivyHttpRepo.module("org.gradle", "publish", "2")
    }

    public void canPublishToAnHttpRepository() {
        given:
        server.start()

        settingsFile << 'rootProject.name = "publish"'
        buildFile << """
apply plugin: 'java'
version = '2'
group = 'org.gradle'

uploadArchives {
    repositories {
        add(new org.apache.ivy.plugins.resolver.URLResolver()) {
            addIvyPattern("${ivyHttpRepo.ivyPattern}")
            addArtifactPattern("${ivyHttpRepo.artifactPattern}")
        }
    }
}
"""

        and:
        module.expectJarPut();
        module.expectIvyPut();

        and:
        executer.withDeprecationChecksDisabled()

        when:
        run 'uploadArchives'

        then:
        module.ivyFile.assertIsFile()
        module.jarFile.assertIsCopyOf(file('build/libs/publish-2.jar'))

        and:
        progressLogging.uploadProgressLogged(module.jarFileUri)
        progressLogging.uploadProgressLogged(module.ivyFileUri)
    }
}
