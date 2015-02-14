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

package org.gradle.integtests.resolve.resource.s3

import org.gradle.integtests.fixtures.AbstractDependencyResolutionTest
import org.gradle.internal.resource.transport.aws.s3.S3ConnectionProperties
import org.gradle.test.fixtures.server.s3.*
import org.junit.Rule

abstract class AbstractS3DependencyResolutionTest extends AbstractDependencyResolutionTest {

    @Rule
    public final S3StubServer server = new S3StubServer()
    final S3StubSupport s3StubSupport = new S3StubSupport(server)

    def setup() {
        executer.withArgument('-i')
        executer.withArgument("-D${S3ConnectionProperties.S3_ENDPOINT_PROPERTY}=${s3StubSupport.endpoint.toString()}")
    }

    String getBucket() {
        return 'tests3bucket'
    }

    abstract String getRepositoryPath()


    MavenS3Repository getMavenS3Repo() {
        new MavenS3Repository(server, file(getTestDirectory()), getRepositoryPath(), getBucket())
    }

    IvyS3Repository getIvyS3Repo() {
        new IvyS3Repository(server, file(getTestDirectory()), getRepositoryPath(), getBucket())
    }

    def assertLocallyAvailableLogged(S3Resource... resources) {
        resources.each {
            assert output.contains("Found locally available resource with matching checksum: [s3:/${it.relativeFilePath()}")
        }
    }

    String mavenAwsRepoDsl() {
        """
        repositories {
            maven {
                url "s3://${getBucket()}${getRepositoryPath()}"
                credentials(AwsCredentials) {
                    accessKey "someKey"
                    secretKey "someSecret"
                }
            }
        }
        """
    }
}

