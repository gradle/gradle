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

package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.HttpServer
import org.gradle.integtests.fixtures.IvyRepository
import org.gradle.integtests.fixtures.MavenRepository
import org.gradle.util.GradleVersion
import org.junit.Rule

import static org.gradle.integtests.fixtures.UserAgentMatcher.matchesNameAndVersion

abstract class AbstractDependencyResolutionTest extends AbstractIntegrationSpec {
    @Rule public final HttpServer server = new HttpServer()

    def "setup"() {
        server.expectUserAgent(matchesNameAndVersion("Gradle", GradleVersion.current().getVersion()))
        requireOwnUserHomeDir()
        writeOsSysPropsToGradleProperties()
    }

    //needed atm, for tests on unknown os
    void writeOsSysPropsToGradleProperties() {
        file("gradle.properties") << System.properties.findAll {key, value -> key.startsWith("os.")}.collect {key, value -> "systemProp.${key}=$value"}.join("\n")
    }

    IvyRepository ivyRepo(def dir = 'ivy-repo') {
        return ivy(dir)
    }

    MavenRepository mavenRepo(String name = "repo") {
        return maven(name)
    }
}
