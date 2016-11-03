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

package org.gradle.internal.resource.transport.gcs

import com.google.api.client.googleapis.testing.auth.oauth2.MockGoogleCredential
import com.google.api.services.storage.Storage
import org.gradle.api.resources.ResourceException
import spock.lang.Ignore
import spock.lang.Specification

class GcsClientTest extends Specification {

    @Ignore
    def "Should upload to gcs"() {
        given:
        Storage storage = Mock(Storage)
        GcsClient client = new GcsClient(storage)
        URI uri = new URI("gcs://localhost/maven/snapshot/myFile.txt")

        when:
        client.put(Mock(InputStream), 12L, uri)
        then:
        1 * storage.putObject(*_) >> { args ->
            Storage.Objects.Insert putObjectRequest = args[0]
            assert putObjectRequest.getBucket() == client.BUCKET_NAME
            assert putObjectRequest.getName() == 'localhost/maven/snapshot/myFile.txt'
            assert putObjectRequest.getHttpContent().length == 12
        }
    }

    def "should extract file name from gcs listing"() {
        GcsClient gcsClient = new GcsClient(Mock(Storage))

        expect:
        gcsClient.extractResourceName(listing) == expected

        where:
        listing         | expected
        '/a/b/file.pom' | 'file.pom'
        '/file.pom'     | 'file.pom'
        '/file.pom'     | 'file.pom'
        '/SNAPSHOT/'    | null
        '/SNAPSHOT/bin' | null
        '/'             | null
    }

//    def "should resolve resource names from an GCS objectlisting"() {
//        setup:
//        GcsClient gcsClient = new GcsClient(Mock(Storage))
//        ObjectListing objectListing = Mock()
//        S3ObjectSummary objectSummary = Mock()
//        objectSummary.getKey() >> '/SNAPSHOT/some.jar'
//
//        S3ObjectSummary objectSummary2 = Mock()
//        objectSummary2.getKey() >> '/SNAPSHOT/someOther.jar'
//        objectListing.getObjectSummaries() >> [objectSummary, objectSummary2]
//
//        when:
//        def results = gcsClient.resolveResourceNames(objectListing)
//
//        then:
//        results == ['some.jar', 'someOther.jar']
//    }

//    def "should make batch call when more than one object listing exists"() {
//        def gcsStorageClient = Mock(Storage)
//        GcsClient gcsClient = new GcsClient(gcsStorageClient)
//        def uri = new URI("gcs://mybucket.com.au/maven/release/")
//        ObjectListing firstListing = Mock()
//        firstListing.isTruncated() >> true
//
//        ObjectListing secondListing = Mock()
//        secondListing.isTruncated() >> false
//
//        when:
//        gcsClient.list(uri)
//
//        then:
//        1 * amazonS3Client.listObjects(_) >> firstListing
//        1 * amazonS3Client.listNextBatchOfObjects(_) >> secondListing
//    }

//    @Ignore
//    def "should apply endpoint override with path style access"() {
//        setup:
//        Optional<URI> someEndpoint = Optional.of(new URI("http://someEndpoint"))
//        S3ConnectionProperties s3Properties = Stub()
//        s3Properties.getEndpoint() >> someEndpoint
//
//        when:
//        S3Client s3Client = new S3Client(credentials(), s3Properties)
//
//        then:
//        s3Client.amazonS3Client.clientOptions.pathStyleAccess == true
//        s3Client.amazonS3Client.endpoint == someEndpoint.get()
//    }

//    @Ignore
//    def "should configure HTTPS proxy"() {
//        setup:
//        S3ConnectionProperties s3Properties = Mock()
//        s3Properties.getProxy() >> Optional.of(new HttpProxySettings.HttpProxy("localhost", 8080, 'username', 'password'))
//        s3Properties.getEndpoint() >> Optional.absent()
//        s3Properties.getMaxErrorRetryCount() >> Optional.absent()
//        when:
//        S3Client s3Client = new S3Client(credentials(), s3Properties)
//
//        then:
//        s3Client.amazonS3Client.clientConfiguration.proxyHost == 'localhost'
//        s3Client.amazonS3Client.clientConfiguration.proxyPort == 8080
//        s3Client.amazonS3Client.clientConfiguration.proxyPassword == 'password'
//        s3Client.amazonS3Client.clientConfiguration.proxyUsername == 'username'
//    }

//    @Ignore
//    def "should not configure HTTPS proxy when non-proxied host"() {
//        setup:
//        HttpProxySettings proxySettings = Mock()
//        proxySettings.getProxy(nonProxied) >> null
//
//        S3ConnectionProperties s3Properties = Mock()
//        s3Properties.getProxy() >> Optional.absent()
//        s3Properties.getEndpoint() >> endpointOverride
//        when:
//
//        S3Client s3Client = new S3Client(credentials(), s3Properties)
//        then:
//        s3Client.amazonS3Client.clientConfiguration.proxyHost == null
//        s3Client.amazonS3Client.clientConfiguration.proxyPort == -1
//        s3Client.amazonS3Client.clientConfiguration.proxyPassword == null
//        s3Client.amazonS3Client.clientConfiguration.proxyUsername == null
//
//        where:
//        nonProxied                                               | endpointOverride
//        com.amazonaws.services.s3.internal.Constants.S3_HOSTNAME | Optional.absent()
//        "mydomain.com"                                           | Optional.absent()
//    }

    @Ignore
    def "should include uri when meta-data not found"() {
        Storage.Objects.Get storageClient = Mock()
        URI uri = new URI("https://somehost/file.txt")
        GcsClient gcsClient = new GcsClient(storageClient)
        Exception exception = new Exception("test exception")
        storageClient.execute(_) >> { throw exception }

        when:
        gcsClient.getMetaData(uri)
        then:
        def ex = thrown(ResourceException)
        ex.message.startsWith("Could not get resource 'https://somehost/file.txt'")
    }

    @Ignore
    def "should include uri when file not found"() {
        Storage.Objects.Get storageClient = Mock()
        URI uri = new URI("https://somehost/file.txt")
        GcsClient gcsClient = new GcsClient(storageClient)
        Exception exception = new Exception("test exception")
        storageClient.execute(_) >> { throw exception }

        when:
        gcsClient.getResource(uri)
        then:
        def ex = thrown(ResourceException)
        ex.message.startsWith("Could not get resource 'https://somehost/file.txt'")
    }

    @Ignore
    def "should include uri when upload fails"() {
        Storage.Objects.Insert storageClient = Mock()
        URI uri = new URI("https://somehost/file.txt")
        GcsClient gcsClient = new GcsClient(storageClient)
        Exception exception = new Exception("test exception")
        storageClient.execute(*_) >> { throw exception }

        when:
        gcsClient.put(Mock(InputStream), 0, uri)
        then:
        def ex = thrown(ResourceException)
        ex.message.startsWith("Could not write to resource 'https://somehost/file.txt'")
    }

    def credentials() {
        def credentials = new MockGoogleCredential()
        credentials.setAccessToken("Access")
        credentials.setRefreshToken("Refresh")
        credentials
    }
}
