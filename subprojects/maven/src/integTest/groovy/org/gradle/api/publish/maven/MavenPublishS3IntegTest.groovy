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


package org.gradle.api.publish.maven

import org.gradle.integtests.fixtures.AbstractMavenPublicationIntegrationSpec
import org.gradle.internal.resource.transport.aws.s3.S3ConnectionProperties
import org.gradle.test.fixtures.maven.MavenRemoteModule
import org.gradle.test.fixtures.maven.ModuleDescriptor
import org.gradle.test.fixtures.server.s3.MavenS3RemoteModule
import org.gradle.test.fixtures.server.s3.S3StubServer
import org.gradle.test.fixtures.server.s3.S3StubSupport
import org.junit.Rule

class MavenPublishS3IntegTest extends AbstractMavenPublicationIntegrationSpec {
    String bucket = 'tests3bucket'
    String repositoryPath = '/maven/release/'

    @Rule
    public final S3StubServer server = new S3StubServer(this)
    final S3StubSupport s3StubSupport = new S3StubSupport(server)

    def setup() {
        executer.withArgument("-D${S3ConnectionProperties.S3_ENDPOINT_PROPERTY}=${s3StubSupport.endpoint.toString()}")
    }

    @Override
    String repositoryUrl() {
        return "s3://$bucket$repositoryPath"
    }

    @Override
    String repositoryCredentials() {
        return """
        credentials(AwsCredentials) {
            accessKey "someKey"
            secretKey "someSecret"
        }"""
    }

    @Override
    MavenRemoteModule createMavenRemoteModule(ModuleDescriptor aModuleDescriptor) {
        new MavenS3RemoteModule(server, this.testDirectory, bucket, repositoryPath, aModuleDescriptor)
    }

    @Override
    String authFailureCause(ModuleDescriptor aModuleDescriptor) {
        "Could not put s3 resource: [${repositoryUrl()}${aModuleDescriptor.artifactDirectory()}/${aModuleDescriptor.artifactName('.jar')}]." +
                " The AWS Access Key Id you provided does not exist in our records."
    }
}
