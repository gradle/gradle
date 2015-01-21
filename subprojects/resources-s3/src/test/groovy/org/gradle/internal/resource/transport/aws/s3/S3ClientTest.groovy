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

import com.google.common.base.Optional
import org.gradle.internal.credentials.DefaultAwsCredentials
import org.gradle.internal.resource.transport.http.HttpProxySettings
import org.jets3t.service.Constants
import org.jets3t.service.S3ServiceException
import org.jets3t.service.impl.rest.httpclient.RestS3Service
import org.jets3t.service.model.S3Object
import spock.lang.Ignore
import spock.lang.Specification

class S3ClientTest extends Specification {

    final S3ConnectionProperties s3SystemProperties = Mock()
    final RestS3Service restS3Service = Mock()

    def "should resolve bucket name from uri"() {
        given:
        URI uri = new URI(uriStr)

        def client = new S3Client(restS3Service, s3SystemProperties)

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
        def client = new S3Client(Mock(RestS3Service), s3SystemProperties)

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
        S3Client client = new S3Client(restS3Service, s3SystemProperties)
        URI uri = new URI("s3://localhost/maven/snapshot/myFile.txt")

        when:
        client.put(Mock(InputStream), 12L, uri)
        then:
        1 * restS3Service.putObject(_, _) >> { args ->
            assert args[0] == 'localhost'
            assert args[1].key == 'maven/snapshot/myFile.txt'
            assert args[1].contentLength == 12
        }
    }

    def "should extract file name from s3 listing"() {
        S3Client s3Client = new S3Client(Mock(RestS3Service), s3SystemProperties)

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
        S3Client s3Client = new S3Client(restS3Service, s3SystemProperties)
        S3Object s3Object1 = Mock(S3Object)
        S3Object s3Object2 = Mock(S3Object)

        s3Object1.getKey() >> '/SNAPSHOT/some.jar'
        s3Object2.getKey() >> '/SNAPSHOT/someOther.jar'
        S3Object[] s3Objects = [s3Object1, s3Object2]

        when:
        def results = s3Client.resolveResourceNames(s3Objects)

        then:
        results == ['some.jar', 'someOther.jar']
    }

    def "should apply endpoint override with dns buckets disabled"() {
        setup:
        Optional<URI> someEndpoint = Optional.of(new URI("http://someEndpoint"))
        S3ConnectionProperties s3Properties = Stub()
        s3Properties.getEndpoint() >> someEndpoint

        when:
        S3Client s3Client = new S3Client(credentials(), s3Properties)

        then:
        s3Client.s3Service.jetS3tProperties.getBoolProperty(S3Client.S3SERVICE_DISABLE_DNS_BUCKETS, false)
        s3Client.s3Service.endpoint == someEndpoint.get().getHost()
    }

    def "should configure HTTPS proxy"() {
        setup:
        S3ConnectionProperties s3Properties = Mock()
        s3Properties.getProxy() >> Optional.of(new HttpProxySettings.HttpProxy("localhost", 8080, 'username', 'password'))
        s3Properties.getEndpoint() >> Optional.absent()
        when:
        S3Client s3Client = new S3Client(credentials(), s3Properties)

        then:
        s3Client.s3Service.jetS3tProperties.getStringProperty(S3Client.HTTPCLIENT_PROXY_HOST, null) == "localhost"
        s3Client.s3Service.jetS3tProperties.getIntProperty(S3Client.HTTPCLIENT_PROXY_PORT, 0) == 8080
        s3Client.s3Service.jetS3tProperties.getStringProperty(S3Client.HTTPCLIENT_PROXY_PASSWORD, null) == "password"
        s3Client.s3Service.jetS3tProperties.getStringProperty(S3Client.HTTPCLIENT_PROXY_USER, null) == "username"
    }

    @Ignore
    def "should not configure HTTPS proxy when non-proxied host"() {
        setup:
        HttpProxySettings proxySettings = Mock()
        proxySettings.getProxy(nonProxied) >> null

        S3ConnectionProperties s3Properties = Mock()
        s3Properties.getProxy() >> Optional.absent()
        s3Properties.getEndpoint() >> endpointOverride
        when:

        S3Client s3Client = new S3Client(credentials(), s3Properties)
        then:
        s3Client.s3Service.jetS3tProperties.properties.getProperty(S3Client.HTTPCLIENT_PROXY_HOST) == null
        s3Client.s3Service.jetS3tProperties.properties.get(S3Client.HTTPCLIENT_PROXY_PORT) == null
        s3Client.s3Service.jetS3tProperties.properties.getProperty(S3Client.HTTPCLIENT_PROXY_PASSWORD) == null
        s3Client.s3Service.jetS3tProperties.properties.getProperty(S3Client.HTTPCLIENT_PROXY_USER) == null

        where:
        nonProxied                    | endpointOverride
        Constants.S3_DEFAULT_HOSTNAME | Optional.absent()
        "mydomain.com"                | Optional.absent()
    }

    def "should include uri when meta-data not found"() {
        RestS3Service restS3Service = Mock()
        URI uri = new URI("https://somehost/file.txt")
        S3Client s3Client = new S3Client(restS3Service, Mock(S3ConnectionProperties))
        S3ServiceException s3ServiceException = new S3ServiceException("test exception")
        restS3Service.getObjectDetails(_, _) >> { throw s3ServiceException }

        when:
        s3Client.getMetaData(uri)
        then:
        def ex = thrown(S3Exception)
        ex.message.startsWith('Could not get s3 meta-data: [https://somehost/file.txt]')
    }

    def "should include uri when file not found"() {
        URI uri = new URI("https://somehost/file.txt")
        S3Client s3Client = new S3Client(restS3Service, Mock(S3ConnectionProperties))
        S3ServiceException s3ServiceException = new S3ServiceException("test exception")
        restS3Service.getObject(_, _) >> { throw s3ServiceException }

        when:
        s3Client.getResource(uri)
        then:
        def ex = thrown(S3Exception)
        ex.message.startsWith('Could not get s3 resource: [https://somehost/file.txt]')
    }

    def "should include uri when upload fails"() {
        URI uri = new URI("https://somehost/file.txt")
        S3Client s3Client = new S3Client(restS3Service, Mock(S3ConnectionProperties))
        S3ServiceException s3ServiceException = new S3ServiceException("test exception")
        restS3Service.putObject(*_) >> { throw s3ServiceException }

        when:
        s3Client.put(Mock(InputStream), 0, uri)
        then:
        def ex = thrown(S3Exception)
        ex.message.startsWith('Could not put s3 resource: [https://somehost/file.txt]')
    }

    def credentials() {
        def credentials = new DefaultAwsCredentials()
        credentials.setAccessKey("AKey")
        credentials.setSecretKey("ASecret")
        credentials
    }
}
