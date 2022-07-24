/*
 * Copyright 2018 the original author or authors.
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

import org.gradle.test.fixtures.HttpRepository
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.test.fixtures.server.http.IvyHttpRepository
import org.gradle.test.fixtures.server.http.MavenHttpRepository
import org.gradle.test.fixtures.server.http.RepositoryHttpServer
import org.junit.Rule

import static org.gradle.internal.resource.transport.http.JavaSystemPropertiesHttpTimeoutSettings.CONNECTION_TIMEOUT_SYSTEM_PROPERTY
import static org.gradle.internal.resource.transport.http.JavaSystemPropertiesHttpTimeoutSettings.SOCKET_TIMEOUT_SYSTEM_PROPERTY

abstract class AbstractHttpDependencyResolutionTest extends AbstractDependencyResolutionTest {
    @Rule
    public final RepositoryHttpServer server = new RepositoryHttpServer(temporaryFolder)

    def setup() {
        executer.beforeExecute {
            executer.withArgument("-D${SOCKET_TIMEOUT_SYSTEM_PROPERTY}=60000")
            executer.withArgument("-D${CONNECTION_TIMEOUT_SYSTEM_PROPERTY}=60000")
        }
    }

    IvyHttpRepository getIvyHttpRepo(HttpRepository.MetadataType metadataType = HttpRepository.MetadataType.DEFAULT) {
        return new IvyHttpRepository(server, "/repo", metadataType, ivyRepo)
    }

    IvyHttpRepository ivyHttpRepo(String name, HttpRepository.MetadataType metadataType = HttpRepository.MetadataType.DEFAULT) {
        assert !name.startsWith("/")
        return new IvyHttpRepository(server, "/${name}", metadataType, ivyRepo(name))
    }

    MavenHttpRepository getMavenHttpRepo(HttpRepository.MetadataType metadataType = HttpRepository.MetadataType.DEFAULT) {
        return new MavenHttpRepository(server, "/repo", metadataType, mavenRepo)
    }

    MavenHttpRepository mavenHttpRepo(String name, HttpRepository.MetadataType metadataType = HttpRepository.MetadataType.DEFAULT) {
        assert !name.startsWith("/")
        return new MavenHttpRepository(server, "/${name}", metadataType, mavenRepo(name))
    }

    MavenHttpRepository mavenHttpRepo(String contextPath, MavenFileRepository backingRepo) {
        return new MavenHttpRepository(server, contextPath, backingRepo)
    }
}
