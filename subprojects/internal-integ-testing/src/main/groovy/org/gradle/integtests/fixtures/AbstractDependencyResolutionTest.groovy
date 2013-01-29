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

package org.gradle.integtests.fixtures

import org.gradle.test.fixtures.ivy.IvyFileRepository
import org.gradle.test.fixtures.ivy.IvyHttpRepository
import org.gradle.test.fixtures.maven.M2Installation
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.test.fixtures.maven.MavenHttpRepository
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.util.GradleVersion
import org.junit.Rule

import static org.gradle.test.matchers.UserAgentMatcher.matchesNameAndVersion

abstract class AbstractDependencyResolutionTest extends AbstractIntegrationSpec {
    @Rule public final HttpServer server = new HttpServer()
    private M2Installation m2Installation = new M2Installation(testDirectory)

    def setup() {
        server.expectUserAgent(matchesNameAndVersion("Gradle", GradleVersion.current().getVersion()))
        requireOwnGradleUserHomeDir()
        executer.beforeExecute m2Installation
    }

    M2Installation getM2Installation() {
        m2Installation
    }

    IvyFileRepository ivyRepo(def dir = 'ivy-repo') {
        return ivy(dir)
    }

    IvyHttpRepository getIvyHttpRepo() {
        return new IvyHttpRepository(server, "/repo", ivyRepo)
    }

    IvyHttpRepository ivyHttpRepo(String name) {
        assert !name.startsWith("/")
        return new IvyHttpRepository(server, "/${name}", ivyRepo(name))
    }

    MavenFileRepository mavenRepo(String name = "repo") {
        return maven(name)
    }

    MavenHttpRepository getMavenHttpRepo() {
        return new MavenHttpRepository(server, "/repo", mavenRepo)
    }

    MavenHttpRepository mavenHttpRepo(String name) {
        assert !name.startsWith("/")
        return new MavenHttpRepository(server, "/${name}", mavenRepo(name))
    }

    MavenHttpRepository mavenHttpRepo(String contextPath, MavenFileRepository backingRepo) {
        return new MavenHttpRepository(server, contextPath, backingRepo)
    }
}
