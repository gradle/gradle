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



package org.gradle.integtests.resolve.artifactreuse

import org.gradle.api.internal.artifacts.ivyservice.DefaultArtifactCacheMetadata
import org.gradle.integtests.fixtures.CrossVersionIntegrationSpec
import org.gradle.integtests.fixtures.executer.UnderDevelopmentGradleDistribution
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.test.fixtures.server.http.MavenHttpRepository
import org.junit.Rule

abstract class AbstractCacheReuseCrossVersionIntegrationTest extends CrossVersionIntegrationSpec {
    @Rule public final HttpServer server = new HttpServer()
    final MavenHttpRepository mavenHttpRepo = new MavenHttpRepository(server, new MavenFileRepository(file("maven-repo")))

    /**
     * **** README ****
     *
     * If this test fails:
     *  1. Make sure DefaultGradleDistribution.getArtifactCacheLayoutVersion settings are correct
     *  2. Think about improving this test so that we don't have to manually fix things ;)
     */
    void setup() {
        assert DefaultArtifactCacheMetadata.CACHE_LAYOUT_VERSION == new UnderDevelopmentGradleDistribution().artifactCacheLayoutVersion
        requireOwnGradleUserHomeDir()
        server.start()
    }
}
