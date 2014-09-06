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

package org.gradle.internal.resource.transport.aws.s3

import com.amazonaws.services.s3.AmazonS3Client
import com.amazonaws.services.s3.model.ObjectListing
import com.amazonaws.services.s3.model.S3ObjectSummary
import org.gradle.internal.credentials.DefaultAwsCredentials
import org.gradle.internal.resource.transport.http.HttpProxySettings
import org.gradle.internal.resource.transport.http.JavaSystemPropertiesSecureHttpProxySettings
import spock.lang.Specification

class S3ClientTest extends Specification {

    final S3ConnectionProperties s3SystemProperties = Mock()

    def "should resolve bucket name from uri"() {
        given:
        URI uri = new URI(uriStr)
        def client = new S3Client(Mock(AmazonS3Client), s3SystemProperties)

        expect:
        client.getBucketName(uri) == expected

        where:
        uriStr                                              || expected
        "s3://localhost"                                    || "localhost"
        "s3://localhost.com/somePath/somePath/filename.txt" || "localhost.com"
        "s3://myaws.com.au"                                 || "myaws.com.au"
        "http://myaws.com.au"                               || "myaws.com.au"
        "https://myaws.com.au"                              || "myaws.com.au"
    }

    def "should resolve s3 bucket key from uri"() {
        given:
        URI uri = new URI(uriStr)
        def client = new S3Client(Mock(AmazonS3Client), s3SystemProperties)

        expect:
        client.getS3BucketKey(uri) == expected

        where:
        uriStr                                     || expected
        's3://localhost/maven/release/myFile.txt'  || 'maven/release/myFile.txt'
        's3://localhost/maven/snapshot/myFile.txt' || 'maven/snapshot/myFile.txt'
        's3://localhost/maven/'                    || 'maven/'

    }

    def "Should upload to s3"() {
        given:
        AmazonS3Client amazonS3Client = Mock()
        S3Client client = new S3Client(amazonS3Client, s3SystemProperties)
        URI uri = new URI("s3://localhost/maven/snapshot/myFile.txt")

        when:
        client.put(Mock(InputStream), 12L, uri)

        then:
        1 * amazonS3Client.putObject(*_) >> { args ->
            assert args[0] == 'localhost'
            assert args[1] == 'maven/snapshot/myFile.txt'
            assert args[3].contentLength == 12
        }
    }

    def "should extract file name from s3 listing"() {
        S3Client s3Client = new S3Client(Mock(AmazonS3Client), s3SystemProperties)

        expect:
        s3Client.extractResourceName(listing) == expected

        where:
        listing         | expected
        '/a/b/file.pom' | 'file.pom'
        '/file.pom'     | 'file.pom'
        '/file.pom'     | 'file.pom'
        '/SNAPSHOT/'    | null
        '/SNAPSHOT/bin' | null
        '/'             | null
    }

    def "should resolve resource names from an AWS objectlisting"() {
        setup:
        S3Client s3Client = new S3Client(Mock(AmazonS3Client), s3SystemProperties)
        ObjectListing objectListing = Mock()
        S3ObjectSummary objectSummary = Mock()
        objectSummary.getKey() >> '/SNAPSHOT/some.jar'
        objectListing.getObjectSummaries() >> [objectSummary]

        when:
        def results = s3Client.resolveResourceNames(objectListing)

        then:
        results == ['some.jar']
    }

    def "should make batch call when more than one object listing exists"() {
        def amazonS3Client = Mock(AmazonS3Client)
        S3Client s3Client = new S3Client(amazonS3Client, s3SystemProperties)
        def uri = new URI("s3://mybucket.com.au/maven/release/")
        ObjectListing firstListing = Mock()
        firstListing.isTruncated() >> true

        ObjectListing secondListing = Mock()
        secondListing.isTruncated() >> false

        when:
        s3Client.list(uri)

        then:
        1 * amazonS3Client.listObjects(_) >> firstListing
        1 * amazonS3Client.listNextBatchOfObjects(_) >> secondListing
    }

    def "should apply endpoint override with path style access"() {
        setup:
        String someEndpoint = "someEndpoint"
        S3ConnectionProperties s3Properties = Stub()
        s3Properties.getEndpoint() >> someEndpoint

        def credentials = new DefaultAwsCredentials()
        credentials.setAccessKey("AKey")
        credentials.setSecretKey("ASecret")

        when:
        S3Client s3Client = new S3Client(credentials, s3Properties)

        then:
        s3Client.amazonS3Client.clientOptions.pathStyleAccess == true
        s3Client.amazonS3Client.endpoint == new URI("https://${someEndpoint}")
    }

    def "should configure https proxy"() {
        setup:
        JavaSystemPropertiesSecureHttpProxySettings proxySettings = Mock(JavaSystemPropertiesSecureHttpProxySettings)
        proxySettings.getProxy() >> new HttpProxySettings.HttpProxy("localhost", 443, 'username', 'password')

        S3ConnectionProperties s3Properties = Mock()
        s3Properties.getProxySettings() >> proxySettings

        def credentials = new DefaultAwsCredentials()
        credentials.setAccessKey("AKey")
        credentials.setSecretKey("ASecret")

        when:
        S3Client s3Client = new S3Client(credentials, s3Properties)

        then:
        s3Client.amazonS3Client.clientConfiguration.proxyHost == 'localhost'
        s3Client.amazonS3Client.clientConfiguration.proxyPort == 443
        s3Client.amazonS3Client.clientConfiguration.proxyPassword == 'password'
        s3Client.amazonS3Client.clientConfiguration.proxyUsername == 'username'
    }
}
