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

import com.google.common.base.Optional
import org.apache.commons.io.IOUtils
import org.gradle.internal.credentials.DefaultAwsCredentials
import org.gradle.internal.resource.transport.aws.s3.S3Client
import org.gradle.internal.resource.transport.aws.s3.S3ConnectionProperties
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.test.fixtures.server.s3.S3StubServer
import org.gradle.test.fixtures.server.s3.S3StubSupport
import org.jets3t.service.model.S3Object
import org.jets3t.service.model.StorageObject
import org.junit.Rule
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class S3ClientIntegrationTest extends Specification {

    public static final String FILE_NAME = "mavenTest.txt"
    final String accessKey = 'gradle-access-key'
    final String secret = 'gradle-secret-key'
    final String bucketName = 'org.gradle.artifacts'

    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()

    @Shared DefaultAwsCredentials awsCredentials = new DefaultAwsCredentials()

    @Rule
    public final S3StubServer server = new S3StubServer()
    final S3StubSupport s3StubSupport = new S3StubSupport(server)

    def setup() {
        awsCredentials.setAccessKey(accessKey)
        awsCredentials.setSecretKey(secret)
    }

    @Unroll
    def "should perform #authenticationType put get and list on an S3 bucket"() {
        setup:
        def fileContents = 'This is only a test'
        File file = temporaryFolder.createFile(FILE_NAME)
        file << fileContents

        s3StubSupport.with {
            stubPutFile(file, "/${bucketName}/maven/release/$FILE_NAME")
            stubMetaData(file, "/${bucketName}/maven/release/$FILE_NAME")
            stubGetFile(file, "/${bucketName}/maven/release/$FILE_NAME")
            stubListFile(temporaryFolder.testDirectory, bucketName)
        }

        S3ConnectionProperties s3SystemProperties = Mock {
            getEndpoint() >> Optional.of(s3StubSupport.endpoint)
            getProxy() >> Optional.fromNullable(null)
        }

        S3Client s3Client = new S3Client(authenticationImpl, s3SystemProperties)

        when:
        def stream = new FileInputStream(file)
        def uri = new URI("s3://${bucketName}/maven/release/$FILE_NAME")
        s3Client.put(stream, file.length(), uri)

        then:
        StorageObject data = s3Client.getMetaData(uri)
        data.getContentLength() == fileContents.length()
        data.getETag() ==~ /\w{32}/

        and:
        S3Object object = s3Client.getResource(uri)
        ByteArrayOutputStream outStream = new ByteArrayOutputStream()
        IOUtils.copyLarge(object.getDataInputStream(), outStream);
        outStream.toString() == fileContents

        and:
        def files = s3Client.list(new URI("s3://${bucketName}/maven/release/"))
        !files.isEmpty()
        files.each {
            assert it.contains(".")
        }

        where:
        authenticationImpl  | authenticationType
        awsCredentials      | "authenticated"
        null                | "anonymous"
    }

    /**
     * Allows for quickly making real aws requests during development
     */
    @Ignore
    def "should interact with real S3"() {
        DefaultAwsCredentials credentials = new DefaultAwsCredentials()
        String bucketName = System.getenv('G_S3_BUCKET')
        credentials.setAccessKey(System.getenv('G_AWS_ACCESS_KEY_ID'))
        credentials.setSecretKey(System.getenv('G_AWS_SECRET_ACCESS_KEY'))
        S3Client s3Client = new S3Client(credentials, new S3ConnectionProperties())

        def fileContents = 'This is only a test'
        File file = temporaryFolder.createFile(FILE_NAME)
        file << fileContents

        expect:
        def stream = new FileInputStream(file)
        def uri = new URI("s3://${bucketName}/maven/release/mavenTest.txt")
        s3Client.put(stream, file.length(), uri)
        s3Client.getResource(new URI("s3://${bucketName}/maven/release/idontExist.txt"))
    }
}
