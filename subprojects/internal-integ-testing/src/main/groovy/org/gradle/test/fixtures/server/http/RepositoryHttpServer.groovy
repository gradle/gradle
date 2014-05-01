/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.test.fixtures.server.http

import org.gradle.test.fixtures.file.TestDirectoryProvider
import org.gradle.test.fixtures.ivy.IvyFileRepository
import org.gradle.test.fixtures.ivy.RemoteIvyRepository
import org.gradle.test.fixtures.server.RepositoryServer
import org.gradle.util.GradleVersion

import static org.gradle.test.matchers.UserAgentMatcher.matchesNameAndVersion

class RepositoryHttpServer extends HttpServer implements RepositoryServer {

    private TestDirectoryProvider testDirectoryProvider

    RepositoryHttpServer(TestDirectoryProvider testDirectoryProvider) {
        this.testDirectoryProvider = testDirectoryProvider
    }

    @Override
    protected void before() throws Throwable {
        start()
        expectUserAgent(matchesNameAndVersion("Gradle", GradleVersion.current().getVersion()))
    }

    private IvyFileRepository getBackingRepository(boolean m2Compatible = false, String dirPattern = null, String ivyFilePattern = null, String artifactFilePattern = null) {
        new IvyFileRepository(testDirectoryProvider.testDirectory.file('ivy-repo'), m2Compatible, dirPattern, ivyFilePattern, artifactFilePattern)
    }

    RemoteIvyRepository getRemoteIvyRepo(boolean m2Compatible = false, String dirPattern = null, String ivyFilePattern = null, String artifactFilePattern = null) {
        return new IvyHttpRepository(this, '/repo', getBackingRepository(m2Compatible, dirPattern, ivyFilePattern, artifactFilePattern), m2Compatible)
    }

    RemoteIvyRepository getRemoteIvyRepo(String contextPath) {
        new IvyHttpRepository(this, contextPath, backingRepository)
    }

    String getValidCredentials() {
        return ""
    }
}
