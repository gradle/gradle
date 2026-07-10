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
import com.amazonaws.services.s3.model.AmazonS3Exception
import com.amazonaws.services.s3.model.CannedAccessControlList
import com.amazonaws.services.s3.model.CompleteMultipartUploadRequest
import com.amazonaws.services.s3.model.InitiateMultipartUploadRequest
import com.amazonaws.services.s3.model.InitiateMultipartUploadResult
import com.amazonaws.services.s3.model.ObjectListing
import com.amazonaws.services.s3.model.PartETag
import com.amazonaws.services.s3.model.PutObjectRequest
import com.amazonaws.services.s3.model.UploadPartRequest
import com.amazonaws.services.s3.model.UploadPartResult
import com.google.common.base.Optional
import org.gradle.api.resources.ResourceException
import org.gradle.internal.resource.transport.http.HttpProxySettings
import org.gradle.util.TestCredentialUtil
import spock.lang.Ignore
import spock.lang.Specification

class S3ClientTest extends Specification {
    final S3ConnectionProperties s3ConnectionProperties = Mock()

    def setup(){
        _ * s3ConnectionProperties.getEndpoint() >> Optional.absent()
        _ * s3ConnectionProperties.getPartSize() >> 512
        _ * s3ConnectionProperties.getMultipartThreshold() >> 1024
    }

    def "Should upload to s3"() {
        given:
        AmazonS3Client amazonS3Client = Mock()
        S3Client client = new S3Client(amazonS3Client, s3ConnectionProperties)
        URI uri = new URI("s3://localhost/maven/snapshot/myFile.txt")

        when:
        client.put(Mock(InputStream), 12L, uri)
        then:
        1 * amazonS3Client.putObject(*_) >> { args ->
            PutObjectRequest putObjectRequest = args[0]
            assert putObjectRequest.bucketName == 'localhost'
            assert putObjectRequest.key == 'maven/snapshot/myFile.txt'
            assert putObjectRequest.cannedAcl == CannedAccessControlList.BucketOwnerFullControl
            assert putObjectRequest.metadata.contentLength == 12
        }
    }

    def "Should upload large files to s3 using the multi-part API"() {
        given:
        AmazonS3Client amazonS3Client = Mock()
        S3ConnectionProperties s3Properties = Stub()
        _ * s3Properties.getEndpoint() >> Optional.absent()
        _ * s3Properties.getPartSize() >> 7
        _ * s3Properties.getMultipartThreshold() >> 10
        S3Client client = new S3Client(amazonS3Client, s3Properties)
        def bucketName = 'localhost'
        def objectKey = 'maven/snapshot/myFile.txt'
        URI uri = new URI("s3://${bucketName}/${objectKey}")
        InitiateMultipartUploadResult initResponse = Mock() {
            getUploadId() >> 1
        }
        UploadPartResult uploadPartResult = Mock() {
            getPartETag() >> Mock(PartETag)
        }

        when:
        client.put(Mock(InputStream), 12L, uri)
        then:
        1 * amazonS3Client.initiateMultipartUpload(*_) >> { args ->
            InitiateMultipartUploadRequest initiateMultipartUploadRequest = args[0]
            assert initiateMultipartUploadRequest.bucketName == bucketName
            assert initiateMultipartUploadRequest.key == objectKey
            assert initiateMultipartUploadRequest.cannedACL == CannedAccessControlList.BucketOwnerFullControl
            initResponse
        }
        2 * amazonS3Client.uploadPart(*_) >> { args ->
            UploadPartRequest uploadPartRequest = args[0]
            assert uploadPartRequest.bucketName == bucketName
            assert uploadPartRequest.key == objectKey
            assert uploadPartRequest.partNumber == 1
            assert uploadPartRequest.fileOffset == 0
            assert uploadPartRequest.partSize == 7
            uploadPartResult
        } >> { args ->
            UploadPartRequest uploadPartRequest = args[0]
            assert uploadPartRequest.bucketName == bucketName
            assert uploadPartRequest.key == objectKey
            assert uploadPartRequest.partNumber == 2
            assert uploadPartRequest.fileOffset == 0
            assert uploadPartRequest.partSize == 5
            uploadPartResult
        }
        1 * amazonS3Client.completeMultipartUpload(*_) >> { args ->
            CompleteMultipartUploadRequest uploadRequest = args[0]
            assert uploadRequest.bucketName == bucketName
            assert uploadRequest.key == objectKey
            assert uploadRequest.partETags.size() == 2
        }
    }

    def "should make batch call when more than one object listing exists"() {
        def amazonS3Client = Mock(AmazonS3Client)
        S3Client s3Client = new S3Client(amazonS3Client, s3ConnectionProperties)
        def uri = new URI("s3://mybucket.com.au/maven/release/")
        ObjectListing firstListing = Mock()
        firstListing.isTruncated() >> true

        ObjectListing secondListing = Mock()
        secondListing.isTruncated() >> false

        when:
        s3Client.listDirectChildren(uri)

        then:
        1 * amazonS3Client.listObjects(_) >> firstListing
        1 * amazonS3Client.listNextBatchOfObjects(_) >> secondListing
    }

    def "should apply endpoint override with path style access"() {
        setup:
        Optional<URI> someEndpoint = Optional.of(new URI("http://someEndpoint"))
        S3ConnectionProperties s3Properties = Stub()
        s3Properties.getEndpoint() >> someEndpoint

        when:
        S3Client s3Client = new S3Client(credentials(), s3Properties)

        then:
        s3Client.amazonS3Client.clientOptions.pathStyleAccess == true
        s3Client.amazonS3Client.endpoint == someEndpoint.get()
    }

    def "should apply endpoint override with path style access without credentials"() {
        setup:
        Optional<URI> someEndpoint = Optional.of(new URI("http://someEndpoint"))
        S3ConnectionProperties s3Properties = Stub()
        s3Properties.getEndpoint() >> someEndpoint

        when:
        S3Client s3Client = new S3Client(s3Properties)

        then:
        s3Client.amazonS3Client.clientOptions.pathStyleAccess == true
        s3Client.amazonS3Client.endpoint == someEndpoint.get()
    }

    def "should configure HTTPS proxy"() {
        setup:
        S3ConnectionProperties s3Properties = Mock()
        s3Properties.getProxy() >> Optional.of(new HttpProxySettings.HttpProxy("localhost", 8080, 'username', 'password'))
        s3Properties.getEndpoint() >> Optional.absent()
        s3Properties.getMaxErrorRetryCount() >> Optional.absent()
        when:
        S3Client s3Client = new S3Client(credentials(), s3Properties)

        then:
        s3Client.amazonS3Client.clientConfiguration.proxyHost == 'localhost'
        s3Client.amazonS3Client.clientConfiguration.proxyPort == 8080
        s3Client.amazonS3Client.clientConfiguration.proxyPassword == 'password'
        s3Client.amazonS3Client.clientConfiguration.proxyUsername == 'username'
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
        s3Client.amazonS3Client.clientConfiguration.proxyHost == null
        s3Client.amazonS3Client.clientConfiguration.proxyPort == -1
        s3Client.amazonS3Client.clientConfiguration.proxyPassword == null
        s3Client.amazonS3Client.clientConfiguration.proxyUsername == null

        where:
        nonProxied                                               | endpointOverride
        com.amazonaws.services.s3.internal.Constants.S3_HOSTNAME | Optional.absent()
        "mydomain.com"                                           | Optional.absent()
    }

    def "should include uri when meta-data not found"() {
        AmazonS3Client amazonS3Client = Mock()
        URI uri = new URI("https://somehost/file.txt")
        S3Client s3Client = new S3Client(amazonS3Client, s3ConnectionProperties)
        AmazonS3Exception amazonS3Exception = new AmazonS3Exception("test exception")
        amazonS3Client.getObject(_) >> { throw amazonS3Exception }

        when:
        s3Client.getMetaData(uri)
        then:
        def ex = thrown(ResourceException)
        ex.message.startsWith("Could not get resource 'https://somehost/file.txt'")
    }

    def "should include uri when file not found"() {
        AmazonS3Client amazonS3Client = Mock()
        URI uri = new URI("https://somehost/file.txt")
        S3Client s3Client = new S3Client(amazonS3Client, s3ConnectionProperties)
        AmazonS3Exception amazonS3Exception = new AmazonS3Exception("test exception")
        amazonS3Client.getObject(_) >> { throw amazonS3Exception }

        when:
        s3Client.getResource(uri)
        then:
        def ex = thrown(ResourceException)
        ex.message.startsWith("Could not get resource 'https://somehost/file.txt'")
    }

    def "should include uri when upload fails"() {
        AmazonS3Client amazonS3Client = Mock()
        URI uri = new URI("https://somehost/file.txt")
        S3Client s3Client = new S3Client(amazonS3Client, s3ConnectionProperties)
        AmazonS3Exception amazonS3Exception = new AmazonS3Exception("test exception")
        amazonS3Client.putObject(*_) >> { throw amazonS3Exception }

        when:
        s3Client.put(Mock(InputStream), 0, uri)
        then:
        def ex = thrown(ResourceException)
        ex.message.startsWith("Could not write to resource 'https://somehost/file.txt'")
    }

    def credentials() {
        def credentials = TestCredentialUtil.defaultAwsCredentials()
        credentials.setAccessKey("AKey")
        credentials.setSecretKey("ASecret")
        credentials
    }
}
