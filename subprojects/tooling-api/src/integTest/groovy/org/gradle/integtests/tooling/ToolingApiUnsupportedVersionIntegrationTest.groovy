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
package org.gradle.integtests.tooling

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.versions.ReleasedVersionDistributions
import org.gradle.integtests.tooling.fixture.ToolingApi
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.UnsupportedVersionException
import org.gradle.tooling.model.GradleProject
import org.gradle.util.GradleVersion

class ToolingApiUnsupportedVersionIntegrationTest extends AbstractIntegrationSpec {
    final ToolingApi toolingApi = new ToolingApi(distribution, temporaryFolder)
    final GradleDistribution otherVersion = new ReleasedVersionDistributions().getDistribution(GradleVersion.version("0.9.2"))
    final URI distroZip = otherVersion.binDistribution.toURI()

    def setup() {
        toolingApi.withConnector { connector -> connector.useDistribution(distroZip) }
        settingsFile.touch()
    }

    def "tooling api reports an error when requesting a model using a gradle version that does not implement the tooling api"() {
        when:
        toolingApi.withConnection { ProjectConnection connection -> connection.getModel(GradleProject.class) }

        then:
        UnsupportedVersionException e = thrown()
        e.message == "The version of Gradle you are using (Gradle distribution '${distroZip}') does not support the ModelBuilder API. Support for this is available in Gradle 1.2 and all later versions."
    }

    def "tooling api reports an error when running a build using a gradle version does not implement the tooling api"() {
        when:
        toolingApi.withConnection { ProjectConnection connection -> connection.newBuild().run() }

        then:
        UnsupportedVersionException e = thrown()
        e.message == "The version of Gradle you are using (Gradle distribution '${distroZip}') does not support the BuildLauncher API. Support for this is available in Gradle 1.2 and all later versions."
    }

    def "tooling api reports an error when running a build action using a gradle version does not implement the tooling api"() {
        when:
        toolingApi.withConnection { ProjectConnection connection -> connection.action(new NullAction()).run() }

        then:
        UnsupportedVersionException e = thrown()
        e.message == "The version of Gradle you are using (Gradle distribution '${distroZip}') does not support the BuildActionExecuter API. Support for this is available in Gradle 1.8 and all later versions."
    }

    def "tooling api reports an error when running tests using a gradle version does not implement the tooling api"() {
        when:
        toolingApi.withConnection { ProjectConnection connection -> connection.newTestLauncher().withJvmTestClasses("class").run() }

        then:
        UnsupportedVersionException e = thrown()
        e.message == "The version of Gradle you are using (Gradle distribution '${distroZip}') does not support the TestLauncher API. Support for this is available in Gradle 2.6 and all later versions."
    }
}
