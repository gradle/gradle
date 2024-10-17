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

package org.gradle.integtests.resource.s3

import com.amazonaws.services.s3.model.CreateBucketRequest
import com.amazonaws.services.s3.model.DeleteBucketRequest
import com.amazonaws.services.s3.model.DeleteObjectRequest
import com.amazonaws.services.s3.model.ListObjectsRequest
import com.amazonaws.services.s3.model.ObjectListing
import com.amazonaws.services.s3.model.Region
import com.amazonaws.services.s3.model.S3Object
import com.amazonaws.services.s3.model.S3ObjectSummary
import com.google.common.base.Optional
import org.apache.commons.io.IOUtils
import org.gradle.api.Action
import org.gradle.integtests.resource.s3.fixtures.S3Server
import org.gradle.internal.IoActions
import org.gradle.internal.credentials.DefaultAwsCredentials
import org.gradle.internal.resource.transport.aws.s3.S3Client
import org.gradle.internal.resource.transport.aws.s3.S3ConnectionProperties
import org.gradle.internal.resource.transport.aws.s3.S3RegionalResource
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.gradle.util.TestCredentialUtil
import org.junit.Rule
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification

class S3ClientIntegrationTest extends Specification {

    public static final String FILE_NAME = "mavenTest.txt"
    final String accessKey = 'gradle-access-key'
    final String secret = 'gradle-secret-key'
    final String bucketName = 'org.gradle.artifacts'

    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    @Shared
    DefaultAwsCredentials awsCredentials = TestCredentialUtil.defaultAwsCredentials()

    @Rule
    public final S3Server server = new S3Server(temporaryFolder)

    def setup() {
        awsCredentials.setAccessKey(accessKey)
        awsCredentials.setSecretKey(secret)
    }

    @Requires(UnitTestPreconditions.Jdk9OrLater)
    def "should perform #authenticationType put get and list on an S3 bucket"() {
        setup:
        def fileContents = 'This is only a test'
        File file = temporaryFolder.createFile(FILE_NAME)
        file << fileContents
        def directSubdirectories = ['some-dir', 'second-dir', 'some-other-dir']
        directSubdirectories.each { temporaryFolder.createDir(it) }

        temporaryFolder.createDir('some-dir', 'not-direct')

        server.stubPutFile(file, "/${bucketName}/maven/release/$FILE_NAME")

        S3ConnectionProperties s3SystemProperties = Mock {
            getEndpoint() >> Optional.of(server.uri)
            getProxy() >> Optional.fromNullable(null)
            getMaxErrorRetryCount() >> Optional.absent()
            getPartSize() >> 512
            getMultipartThreshold() >> 1024
        }

        S3Client s3Client = new S3Client(authenticationImpl, s3SystemProperties)

        when:
        def stream = new FileInputStream(file)
        def uri = new URI("s3://${bucketName}/maven/release/$FILE_NAME")

        then:
        s3Client.put(stream, file.length(), uri)

        when:
        server.stubMetaData(file, "/${bucketName}/maven/release/$FILE_NAME")
        S3Object data = s3Client.getMetaData(uri)
        def metadata = null
        IoActions.withResource(data, {
            metadata = data.getObjectMetadata()
        } as Action)

        then:
        metadata.getContentLength() == 0
        metadata.getETag() ==~ /\w{32}/

        when:
        server.stubGetFile(file, "/${bucketName}/maven/release/$FILE_NAME")

        then:
        S3Object object = s3Client.getResource(uri)
        IoActions.withResource(object, {
            object.metadata.getContentLength() == fileContents.length()
            object.metadata.getETag() ==~ /\w{32}/
            ByteArrayOutputStream outStream = new ByteArrayOutputStream()
            IOUtils.copyLarge(object.getObjectContent(), outStream);
            outStream.toString() == fileContents
        } as Action)

        when:
        server.stubListFile(temporaryFolder.testDirectory, bucketName)

        then:
        def listing = s3Client.listDirectChildren(new URI("s3://${bucketName}/maven/release/"))
        listing as Set == ([FILE_NAME] + directSubdirectories) as Set

        where:
        authenticationImpl | authenticationType
        awsCredentials     | "authenticated"
        null               | "anonymous"
    }

    /**
     * Allows for quickly making real aws requests during development
     */
    @Ignore
    def "should interact with real S3 using KEY/SECRET pair"() {
        DefaultAwsCredentials credentials = TestCredentialUtil.defaultAwsCredentials()
        String bucketName = System.getenv('G_S3_BUCKET')
        credentials.setAccessKey(System.getenv('G_AWS_ACCESS_KEY_ID'))
        credentials.setSecretKey(System.getenv('G_AWS_SECRET_ACCESS_KEY'))
        S3Client s3Client = new S3Client(credentials, new S3ConnectionProperties())

        def fileContents = 'This is only a test'
        File file = temporaryFolder.createFile(FILE_NAME)
        file << fileContents

        expect:
        def stream = new FileInputStream(file)
        def uri = new URI("s3://${bucketName}/maven/release/${new Date().getTime()}-mavenTest.txt")
        s3Client.put(stream, file.length(), uri)
        s3Client.getResource(new URI("s3://${bucketName}/maven/release/idontExist.txt")).close()
    }

    @Ignore
    def "should interact with real S3 using KEY/SECRET/TOKEN triplet"() {
        DefaultAwsCredentials credentials = TestCredentialUtil.defaultAwsCredentials()
        String bucketName = System.getenv('G_S3_BUCKET')
        credentials.setAccessKey(System.getenv('G_AWS_ACCESS_KEY_ID'))
        credentials.setSecretKey(System.getenv('G_AWS_SECRET_ACCESS_KEY'))
        credentials.setSessionToken(System.getenv('G_AWS_SESSION_TOKEN'))

        S3Client s3Client = new S3Client(credentials, new S3ConnectionProperties())

        def fileContents = 'This is only a test'
        File file = temporaryFolder.createFile(FILE_NAME)
        file << fileContents

        expect:
        def stream = new FileInputStream(file)
        def uri = new URI("s3://${bucketName}/maven/release/${new Date().getTime()}-mavenTest.txt")
        s3Client.put(stream, file.length(), uri)
        s3Client.getResource(new URI("s3://${bucketName}/maven/release/idontExist.txt"))
    }

    @Ignore
    def "should interact with real S3 using SDK delegation"() {
        String bucketName = System.getenv('G_S3_BUCKET')

        S3Client s3Client = new S3Client(new S3ConnectionProperties())

        def fileContents = 'This is only a test'
        File file = temporaryFolder.createFile(FILE_NAME)
        file << fileContents

        expect:
        def stream = new FileInputStream(file)
        def uri = new URI("s3://${bucketName}/maven/release/${new Date().getTime()}-mavenTest.txt")
        s3Client.put(stream, file.length(), uri)
        s3Client.getResource(new URI("s3://${bucketName}/maven/release/idontExist.txt"))
    }


    @Ignore
    def "should use region specific endpoints to interact with buckets in all regions"() {
        setup:
        String bucketPrefix = 'testv4signatures'
        DefaultAwsCredentials credentials = TestCredentialUtil.defaultAwsCredentials()
        credentials.setAccessKey(System.getenv('G_AWS_ACCESS_KEY_ID'))
        credentials.setSecretKey(System.getenv('G_AWS_SECRET_ACCESS_KEY'))

        S3Client s3Client = new S3Client(credentials, new S3ConnectionProperties())

        def fileContents = 'This is only a test'
        File file = temporaryFolder.createFile(FILE_NAME)
        file << fileContents

        expect:
        (Region.values() - [Region.US_GovCloud, Region.CN_Beijing]).each { Region region ->
            String bucketName = "${bucketPrefix}-${region ?: region.name}"

            String key = "/maven/release/test.txt"
            String regionForUrl = region == Region.US_Standard ? "s3.amazonaws.com" : "s3-${region.getFirstRegionId()}.amazonaws.com"
            def uri = new URI("s3://${bucketName}.${regionForUrl}${key}")

            S3RegionalResource s3RegionalResource = new S3RegionalResource(uri)
            s3Client.amazonS3Client.setRegion(s3RegionalResource.getRegion().get())


            println "Regional uri: ${uri}"
            println("Creating bucket: ${bucketName}")
            CreateBucketRequest createBucketRequest = new CreateBucketRequest(bucketName, region)
            s3Client.amazonS3Client.createBucket(createBucketRequest)

            println "-- uploading"
            s3Client.put(new FileInputStream(file), file.length(), uri)

            println "------Getting object"
            s3Client.getResource(uri).close()

            ListObjectsRequest listObjectsRequest = new ListObjectsRequest()
                    .withBucketName(bucketName)

            ObjectListing objects = s3Client.amazonS3Client.listObjects(listObjectsRequest)
            objects.objectSummaries.each { S3ObjectSummary summary ->
                println "-- Deleting object ${summary.getKey()}"
                DeleteObjectRequest deleteObjectRequest = new DeleteObjectRequest(bucketName, summary.getKey())
                s3Client.amazonS3Client.deleteObject(deleteObjectRequest)
            }

            println("Deleting bucket: ${bucketName}")
            DeleteBucketRequest deleteBucketRequest = new DeleteBucketRequest(bucketName)
            s3Client.amazonS3Client.deleteBucket(deleteBucketRequest)
        }
    }
}
