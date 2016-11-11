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

package org.gradle.integtests.resource.gcs

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.integtests.resource.gcs.fixtures.GcsServer
import org.gradle.integtests.resource.gcs.fixtures.IvyGcsRepository
import org.gradle.integtests.resource.gcs.fixtures.MavenGcsRepository
import org.junit.Rule

abstract class AbstractGcsDependencyResolutionTest extends AbstractDependencyResolutionTest {

    @Rule
    public final GcsServer server = new GcsServer(temporaryFolder)

    def setup() {
        executer.withArgument('-i')
        executer.withArgument("-Dorg.gradle.gcs.endpoint=${server.uri}")
    }

    String getBucket() {
        return 'testGcsbucket'
    }

    abstract String getRepositoryPath()


    MavenGcsRepository getMavenGcsRepo() {
        new MavenGcsRepository(server, file(getTestDirectory()), getRepositoryPath(), getBucket())
    }

    IvyGcsRepository getIvyGcsRepo() {
        new IvyGcsRepository(server, file(getTestDirectory()), getRepositoryPath(), getBucket())
    }

//    def assertLocallyAvailableLogged(GcsResource... resources) {
//        resources.each {
//            assert output.contains("Found locally available resource with matching checksum: [gcs:/${it.relativeFilePath()}")
//        }
//    }

    String mavenGcsRepoDsl() {
        """
        repositories {
            maven {
                url "${mavenGcsRepo.uri}"
            }
        }
        """
    }
}

