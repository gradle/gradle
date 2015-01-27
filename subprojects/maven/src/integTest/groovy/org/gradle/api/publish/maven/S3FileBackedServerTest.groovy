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

import com.google.common.base.Optional
import org.apache.commons.io.IOUtils
import org.gradle.internal.credentials.DefaultAwsCredentials
import org.gradle.internal.resource.transport.aws.s3.S3Client
import org.gradle.internal.resource.transport.aws.s3.S3ConnectionProperties
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.test.fixtures.server.s3.S3FileBackedServer
import org.jets3t.service.model.S3Object
import org.jets3t.service.model.StorageObject
import org.junit.Rule
import spock.lang.Specification

class S3FileBackedServerTest extends Specification {

    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()
    public final S3FileBackedServer server = new S3FileBackedServer(temporaryFolder.getTestDirectory())
    final DefaultAwsCredentials awsCredentials = new DefaultAwsCredentials()
    final String bucketName = 'test-bucket'

    def setup() {
        awsCredentials.setAccessKey('key')
        awsCredentials.setSecretKey('secret')
    }

    def "should put and get a file"() {
        def fileContents = 'This is only a test'
        File file = temporaryFolder.createFile('test.txt')
        file << fileContents

        S3ConnectionProperties s3SystemProperties = Mock {
            getEndpoint() >> Optional.of(server.uri)
            getProxy() >> Optional.fromNullable(null)
        }

        S3Client s3Client = new S3Client(awsCredentials, s3SystemProperties)

        when:
        def stream = new FileInputStream(file)
        def uri = new URI("s3://${bucketName}/maven/release/${file.getName()}")
        s3Client.put(stream, file.length(), uri)

        then:
        StorageObject data = s3Client.getMetaData(uri)
        data.getContentLength() == fileContents.length()
        data.getETag() ==~ /\w{32}/

        then:
        S3Object object = s3Client.getResource(uri)
        ByteArrayOutputStream outStream = new ByteArrayOutputStream()
        IOUtils.copyLarge(object.getDataInputStream(), outStream);
        outStream.toString() == fileContents

    }
}
