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

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import org.gradle.integtests.resource.gcs.fixtures.GcsServer
import org.gradle.internal.credentials.DefaultAwsCredentials
import org.gradle.internal.resource.transport.gcs.GcsClient
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification

class GcsClientIntegrationTest extends Specification {

    public static final String FILE_NAME = "mavenTest.txt"
    final String accessKey = 'gradle-access-key'
    final String secret = 'gradle-secret-key'
    final String bucketName = 'org.gradle.artifacts'

    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()

    @Shared
    GoogleCredential gcsCredentials = new GoogleCredential()

    @Rule
    public final GcsServer server = new GcsServer(temporaryFolder)

    def setup() {
        gcsCredentials.refreshToken();
    }

//    @Unroll
//    def "should perform #authenticationType put get and list on an Gcs bucket"() {
//        setup:
//        def fileContents = 'This is only a test'
//        File file = temporaryFolder.createFile(FILE_NAME)
//        file << fileContents
//
//        server.stubPutFile(file, "/${bucketName}/maven/release/$FILE_NAME")
//
//        GcsConnectionProperties gcsSystemProperties = Mock {
//            getEndpoint() >> Optional.of(server.uri)
//            getProxy() >> Optional.fromNullable(null)
//            getMaxErrorRetryCount() >> Optional.absent()
//        }
//
//        GcsClient gcsClient = new GcsClient(authenticationImpl, gcsSystemProperties)
//
//        when:
//        def stream = new FileInputStream(file)
//        def uri = new URI("gcs://${bucketName}/maven/release/$FILE_NAME")
//
//        then:
//        gcsClient.put(stream, file.length(), uri)
//
//        when:
//        server.stubMetaData(file, "/${bucketName}/maven/release/$FILE_NAME")
//        GcsObject data = gcsClient.getMetaData(uri)
//        def metadata = data.getObjectMetadata()
//
//        then:
//        metadata.getContentLength() == 0
//        metadata.getETag() ==~ /\w{32}/
//
//        when:
//        server.stubGetFile(file, "/${bucketName}/maven/release/$FILE_NAME")
//
//        then:
//        GcsObject object = gcsClient.getResource(uri)
//        object.metadata.getContentLength() == fileContents.length()
//        object.metadata.getETag() ==~ /\w{32}/
//        ByteArrayOutputStream outStream = new ByteArrayOutputStream()
//        IOUtils.copyLarge(object.getObjectContent(), outStream);
//        outStream.toString() == fileContents
//
//        when:
//        server.stubListFile(temporaryFolder.testDirectory, bucketName)
//
//        then:
//        def files = gcsClient.list(new URI("gcs://${bucketName}/maven/release/"))
//        !files.isEmpty()
//        files.each {
//            assert it.contains(".")
//        }
//
//        where:
//        authenticationImpl | authenticationType
//        gcsCredentials     | "authenticated"
//        null               | "anonymous"
//    }

    /**
     * Allows for quickly making real aws requests during development
     */
    @Ignore
    def "should interact with real Gcs"() {
        String bucketName = System.getenv('G_Gcs_BUCKET')
        GoogleCredential credential = GoogleCredential.getApplicationDefault()
        GcsClient gcsClient = new GcsClient(credential)

        def fileContents = 'This is only a test'
        File file = temporaryFolder.createFile(FILE_NAME)
        file << fileContents

        expect:
        def stream = new FileInputStream(file)
        def uri = new URI("gcs://${bucketName}/maven/release/${new Date().getTime()}-mavenTest.txt")
        gcsClient.put(stream, file.length(), uri)
        gcsClient.getResource(new URI("gcs://${bucketName}/maven/release/idontExist.txt"))
    }

//    @Ignore
//    def "should use region specific endpoints to interact with buckets in all regions"() {
//        setup:
//        String bucketPrefix = 'testv4signatures'
//        DefaultAwsCredentials credentials = new DefaultAwsCredentials()
//        credentials.setAccessKey(System.getenv('G_AWS_ACCESS_KEY_ID'))
//        credentials.setSecretKey(System.getenv('G_AWS_SECRET_ACCESS_KEY'))
//
//        GcsClient gcsClient = new GcsClient(credentials, new GcsConnectionProperties())
//
//        def fileContents = 'This is only a test'
//        File file = temporaryFolder.createFile(FILE_NAME)
//        file << fileContents
//
//        expect:
//        (Region.values() - [Region.US_GovCloud, Region.CN_Beijing]).each { Region region ->
//            String bucketName = "${bucketPrefix}-${region ?: region.name}"
//
//            String key = "/maven/release/test.txt"
//            String regionForUrl = region == Region.US_Standard ? "gcs.amazonaws.com" : "gcs-${region.getFirstRegionId()}.amazonaws.com"
//            def uri = new URI("gcs://${bucketName}.${regionForUrl}${key}")
//
//            GcsRegionalResource gcsRegionalResource = new GcsRegionalResource(uri)
//            gcsClient.amazonGcsClient.setRegion(gcsRegionalResource.region)
//
//
//            println "Regional uri: ${uri}"
//            println("Creating bucket: ${bucketName}")
//            CreateBucketRequest createBucketRequest = new CreateBucketRequest(bucketName, region)
//            gcsClient.amazonGcsClient.createBucket(createBucketRequest)
//
//            println "-- uploading"
//            gcsClient.put(new FileInputStream(file), file.length(), uri)
//
//            println "------Getting object"
//            gcsClient.getResource(uri)
//
//            ListObjectsRequest listObjectsRequest = new ListObjectsRequest()
//                    .withBucketName(bucketName)
//
////            ObjectListing objects = gcsClient.amazonGcsClient.listObjects(listObjectsRequest)
////            objects.objectSummaries.each { GcsObjectSummary summary ->
////                println "-- Deleting object ${summary.getKey()}"
////                DeleteObjectRequest deleteObjectRequest = new DeleteObjectRequest(bucketName, summary.getKey())
////                s3Client.amazonS3Client.deleteObject(deleteObjectRequest)
////            }
//
//            println("Deleting bucket: ${bucketName}")
//            DeleteBucketRequest deleteBucketRequest = new DeleteBucketRequest(bucketName)
//            gcsClient.googleStorageClient.deleteBucket(deleteBucketRequest)
//        }
//    }
}
