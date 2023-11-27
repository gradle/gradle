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

import software.amazon.awssdk.awscore.exception.AwsErrorDetails
import software.amazon.awssdk.core.client.config.SdkClientOption
import software.amazon.awssdk.services.s3.internal.endpoints.S3EndpointUtils
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse
import software.amazon.awssdk.services.s3.model.ObjectCannedACL
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable
import software.amazon.awssdk.services.s3control.model.S3CannedAccessControlList
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.UploadPartRequest
import software.amazon.awssdk.services.s3.model.UploadPartResponse
import com.google.common.base.Optional
import org.gradle.api.resources.ResourceException
import org.gradle.internal.credentials.DefaultAwsCredentials
import org.gradle.internal.resource.transport.http.HttpProxySettings
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
        software.amazon.awssdk.services.s3.S3Client amazonS3Client = Mock()
        S3Client client = new S3Client(amazonS3Client, s3ConnectionProperties)
        URI uri = new URI("s3://localhost/maven/snapshot/myFile.txt")

        when:
        client.put(Mock(InputStream), 12L, uri)
        then:
        1 * amazonS3Client.putObject(*_) >> { args ->
            PutObjectRequest putObjectRequest = args[0]
            assert putObjectRequest.bucket == 'localhost'
            assert putObjectRequest.key == 'maven/snapshot/myFile.txt'
            assert putObjectRequest.acl == S3CannedAccessControlList.BUCKET_OWNER_FULL_CONTROL.toString()
            assert putObjectRequest.contentLength == 12
        }
    }

    def "Should upload large files to s3 using the multi-part API"() {
        given:
        def bucketName = 'localhost'
        def objectKey = 'maven/snapshot/myFile.txt'
        CreateMultipartUploadRequest initRequest = CreateMultipartUploadRequest.builder()
            .bucket(bucketName)
            .key(objectKey)
            .acl(ObjectCannedACL.BUCKET_OWNER_FULL_CONTROL)
            .build()
        CreateMultipartUploadResponse initResponse = CreateMultipartUploadResponse
            .builder()
            .uploadId('1')
            .build()
        software.amazon.awssdk.services.s3.S3Client amazonS3Client = Mock()
        S3ConnectionProperties s3Properties = Mock()
        _ * s3Properties.getEndpoint() >> Optional.absent()
        _ * s3Properties.getPartSize() >> 7
        _ * s3Properties.getMultipartThreshold() >> 10
        S3Client client = new S3Client(amazonS3Client, s3Properties)

        URI uri = new URI("s3://${bucketName}/${objectKey}")

        UploadPartResponse uploadPartResult = UploadPartResponse
            .builder()
            .eTag('eTag')
            .build()

        when:
        client.put(Mock(InputStream), 12L, uri)
        then:
        1 * amazonS3Client.createMultipartUpload(*_) >> { args ->
            CreateMultipartUploadRequest createMultipartUpload = args[0]
            assert createMultipartUpload.bucket == bucketName
            assert createMultipartUpload.key == objectKey
            assert createMultipartUpload.acl == S3CannedAccessControlList.BUCKET_OWNER_FULL_CONTROL.toString()
            initResponse
        }
        2 * amazonS3Client.uploadPart(*_) >> { args ->
            UploadPartRequest uploadPartRequest = args[0]
            assert uploadPartRequest.bucket == bucketName
            assert uploadPartRequest.key == objectKey
            assert uploadPartRequest.partNumber == 1
            uploadPartResult
        } >> { args ->
            UploadPartRequest uploadPartRequest = args[0]
            assert uploadPartRequest.bucket == bucketName
            assert uploadPartRequest.key == objectKey
            assert uploadPartRequest.partNumber == 2
            uploadPartResult
        }
        1 * amazonS3Client.completeMultipartUpload(*_) >> { args ->
            CompleteMultipartUploadRequest uploadRequest = args[0]
            assert uploadRequest.bucket == bucketName
            assert uploadRequest.key == objectKey
        }
    }

    def "should make batch call when more than one object listing exists"() {
        ListObjectsV2Iterable listObjectsV2Iterable = Mock()
        software.amazon.awssdk.services.s3.S3Client amazonS3Client = Mock() {
            listObjectsV2Paginator(_) >> listObjectsV2Iterable
        }

        S3Client s3Client = new S3Client(amazonS3Client, s3ConnectionProperties)

        def uri = new URI("s3://mybucket.com.au/maven/release/")

        when:
        s3Client.listDirectChildren(uri)

        then:
        1 * listObjectsV2Iterable.forEach(_)
    }

    def "should apply endpoint override with path style access"() {
        setup:
        Optional<URI> someEndpoint = Optional.of(new URI("http://someEndpoint"))
        S3ConnectionProperties s3Properties = Stub()
        s3Properties.getEndpoint() >> someEndpoint

        when:
        S3Client s3Client = new S3Client(credentials(), s3Properties)
        def amazonS3Client = s3Client.build()
        def serviceConfiguration = amazonS3Client.clientConfiguration.option(SdkClientOption.SERVICE_CONFIGURATION)

        then:
        S3EndpointUtils.isPathStyleAccessEnabled(serviceConfiguration) == true
        amazonS3Client.serviceClientConfiguration().endpointOverride().get() == someEndpoint.get()
    }

    def "should apply endpoint override with path style access without credentials"() {
        setup:
        Optional<URI> someEndpoint = Optional.of(new URI("http://someEndpoint"))
        S3ConnectionProperties s3Properties = Stub()
        s3Properties.getEndpoint() >> someEndpoint

        when:
        S3Client s3Client = new S3Client(s3Properties)
        def amazonS3Client = s3Client.build()
        def serviceConfiguration = amazonS3Client.clientConfiguration.option(SdkClientOption.SERVICE_CONFIGURATION)

        then:
        S3EndpointUtils.isPathStyleAccessEnabled(serviceConfiguration) == true
        amazonS3Client.serviceClientConfiguration().endpointOverride().get() == someEndpoint.get()
    }

    def "should configure HTTPS proxy"() {
        setup:
        S3ConnectionProperties s3Properties = Stub()
        s3Properties.getProxy() >> Optional.of(new HttpProxySettings.HttpProxy("localhost", 8080, 'username', 'password'))
        s3Properties.getEndpoint() >> Optional.absent()
        s3Properties.getMaxErrorRetryCount() >> Optional.absent()
        when:
        S3Client s3Client = new S3Client(credentials(), s3Properties)
        def amazonS3Client = s3Client.build()
        def clientConfiguration = amazonS3Client.clientConfiguration.option(SdkClientOption.SYNC_HTTP_CLIENT)

        then:
        clientConfiguration.requestConfig.proxyConfiguration.host == 'localhost'
        clientConfiguration.requestConfig.proxyConfiguration.port == 8080
        clientConfiguration.requestConfig.proxyConfiguration.password == 'password'
        clientConfiguration.requestConfig.proxyConfiguration.username == 'username'
    }

    def "should not configure HTTPS proxy when non-proxied host"() {
        setup:
        HttpProxySettings proxySettings = Stub()
        proxySettings.getProxy(nonProxied) >> null

        S3ConnectionProperties s3Properties = Stub()
        s3Properties.getProxy() >> Optional.absent()
        s3Properties.getEndpoint() >> endpointOverride

        when:
        S3Client s3Client = new S3Client(credentials(), s3Properties)
        def amazonS3Client = s3Client.build()
        def clientConfiguration = amazonS3Client.clientConfiguration.option(SdkClientOption.SYNC_HTTP_CLIENT)

        then:
        clientConfiguration.requestConfig.proxyConfiguration.host == null
        clientConfiguration.requestConfig.proxyConfiguration.port == 0
        clientConfiguration.requestConfig.proxyConfiguration.password == null
        clientConfiguration.requestConfig.proxyConfiguration.username == null

        where:
        nonProxied                                               | endpointOverride
        org.gradle.internal.resource.transport.aws.s3.S3ConnectionProperties.S3_HOSTNAME | Optional.absent()
        "mydomain.com"                                           | Optional.absent()
    }

    def "should include uri when meta-data not found"() {
        software.amazon.awssdk.services.s3.S3Client amazonS3Client = Stub()
        URI uri = new URI("https://somehost/file.txt")
        S3Client s3Client = new S3Client(amazonS3Client, s3ConnectionProperties)
        S3Exception amazonS3Exception = S3Exception.builder()
            .awsErrorDetails(AwsErrorDetails.builder().errorMessage("test exception").build())
            .build()
        amazonS3Client.headObject(_) >> { throw amazonS3Exception }

        when:
        s3Client.getMetaData(uri)
        then:
        def ex = thrown(ResourceException)
        ex.message.startsWith("Could not get resource 'https://somehost/file.txt'")
    }

    def "should include uri when file not found"() {
        software.amazon.awssdk.services.s3.S3Client amazonS3Client = Stub()
        URI uri = new URI("https://somehost/file.txt")
        S3Client s3Client = new S3Client(amazonS3Client, s3ConnectionProperties)
        S3Exception amazonS3Exception = S3Exception.builder()
            .awsErrorDetails(AwsErrorDetails.builder().errorMessage("test exception").build())
            .build()
        amazonS3Client.getObject(_) >> { throw amazonS3Exception }

        when:
        s3Client.getResource(uri)
        then:
        def ex = thrown(ResourceException)
        ex.message.startsWith("Could not get resource 'https://somehost/file.txt'")
    }

    def "should include uri when upload fails"() {
        software.amazon.awssdk.services.s3.S3Client amazonS3Client = Stub()
        URI uri = new URI("https://somehost/file.txt")
        S3ConnectionProperties s3Properties = Stub()
        s3Properties.getProxy() >> Optional.absent()
        S3Client s3Client = new S3Client(s3Properties)

        S3Exception amazonS3Exception = S3Exception.builder()
            .awsErrorDetails(AwsErrorDetails.builder().errorMessage("test exception").build())
            .build()
        amazonS3Client.putObject(*_) >> { throw amazonS3Exception }

        when:
        s3Client.put(Mock(InputStream), 0, uri)
        then:
        def ex = thrown(ResourceException)
        ex.message.startsWith("Could not write to resource 'https://somehost/file.txt'")
    }

    def credentials() {
        def credentials = new DefaultAwsCredentials()
        credentials.setAccessKey("AKey")
        credentials.setSecretKey("ASecret")
        credentials
    }
}
