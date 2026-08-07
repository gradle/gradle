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

import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider
import software.amazon.awssdk.awscore.exception.AwsErrorDetails
import software.amazon.awssdk.core.client.config.SdkClientOption
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
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
import org.gradle.api.resources.ResourceException
import org.gradle.internal.resource.transport.http.HttpProxySettings
import org.gradle.util.TestCredentialUtil
import spock.lang.Specification


class S3ClientTest extends Specification {
    final S3ConnectionProperties s3ConnectionProperties = Mock()

    def setup(){
        _ * s3ConnectionProperties.getEndpoint() >> Optional.empty()
        _ * s3ConnectionProperties.getPartSize() >> 512
        _ * s3ConnectionProperties.getMultipartThreshold() >> 1024
    }

    def "Should upload to s3"() {
        given:
        software.amazon.awssdk.services.s3.S3Client amazonS3Client = Mock()
        S3Client client = new S3Client(amazonS3Client, s3ConnectionProperties)
        URI uri = new URI("s3://localhost/maven/snapshot/myFile.txt")

        when:
        client.put(new ByteArrayInputStream(new byte[12]), 12L, uri)
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
        _ * s3Properties.getEndpoint() >> Optional.empty()
        _ * s3Properties.getPartSize() >> 7
        _ * s3Properties.getMultipartThreshold() >> 10
        S3Client client = new S3Client(amazonS3Client, s3Properties)

        URI uri = new URI("s3://${bucketName}/${objectKey}")

        UploadPartResponse uploadPartResult = UploadPartResponse
            .builder()
            .eTag('eTag')
            .build()

        when:
        client.put(new ByteArrayInputStream(new byte[12]), 12L, uri)
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
        s3Properties.getEndpoint() >> Optional.empty()
        s3Properties.getMaxErrorRetryCount() >> Optional.empty()
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
        s3Properties.getProxy() >> Optional.empty()
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
        org.gradle.internal.resource.transport.aws.s3.S3ConnectionProperties.S3_HOSTNAME | Optional.empty()
        "mydomain.com"                                           | Optional.empty()
    }

    def "getMetaData wraps generic S3Exception with the resource uri"() {
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
        s3Properties.getProxy() >> Optional.empty()
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

    def "should map numRetries N to maxAttempts N+1 on the built client"() {
        given:
        S3ConnectionProperties s3Properties = Stub()
        s3Properties.getEndpoint() >> Optional.empty()
        s3Properties.getProxy() >> Optional.empty()
        s3Properties.getMaxErrorRetryCount() >> Optional.of(3)
        S3Client s3Client = new S3Client(credentials(), s3Properties)

        when:
        def amazonS3Client = s3Client.build()
        def retryStrategy = amazonS3Client.clientConfiguration.option(SdkClientOption.RETRY_STRATEGY)

        then:
        // numRetries counts retries after the initial attempt; maxAttempts counts total.
        // A "fix" from +1 to +0 would silently reduce retries by one.
        retryStrategy.maxAttempts() == 4
    }

    def "should default region to US_EAST_1 when no bucket-derived region is available"() {
        given:
        S3ConnectionProperties s3Properties = Stub()
        s3Properties.getEndpoint() >> Optional.empty()
        s3Properties.getProxy() >> Optional.empty()
        s3Properties.getMaxErrorRetryCount() >> Optional.empty()
        S3Client s3Client = new S3Client(credentials(), s3Properties)

        when:
        def amazonS3Client = s3Client.build()

        then:
        amazonS3Client.serviceClientConfiguration().region() == Region.US_EAST_1
    }

    def "should default to AnonymousCredentialsProvider when no credentials given"() {
        given:
        S3ConnectionProperties s3Properties = Stub()
        s3Properties.getEndpoint() >> Optional.empty()
        s3Properties.getProxy() >> Optional.empty()
        s3Properties.getMaxErrorRetryCount() >> Optional.empty()
        S3Client s3Client = new S3Client((org.gradle.api.credentials.AwsCredentials) null, s3Properties)

        when:
        def amazonS3Client = s3Client.build()

        then:
        // Guards against a regression to null/DefaultCredentialsProvider, which would leak IMDS
        // lookups and break anonymous S3 access to public buckets.
        amazonS3Client.serviceClientConfiguration().credentialsProvider() instanceof AnonymousCredentialsProvider
    }

    def "put buffers body so RequestBody can be re-read by SDK signing/retries"() {
        given:
        software.amazon.awssdk.services.s3.S3Client amazonS3Client = Mock()
        S3Client s3Client = new S3Client(amazonS3Client, s3ConnectionProperties)
        URI uri = new URI("s3://localhost/foo.txt")
        byte[] expected = 'hello world'.bytes
        RequestBody captured = null

        when:
        // Non-markable stream: a regression from RequestBody.fromBytes back to fromInputStream
        // would break at the second .newStream() call below because the underlying InputStream
        // would be exhausted and mark/reset unsupported.
        s3Client.put(new NonMarkableInputStream(expected), expected.length as long, uri)

        then:
        1 * amazonS3Client.putObject(*_) >> { args ->
            captured = args[1] as RequestBody
            null
        }

        and:
        captured.contentStreamProvider().newStream().bytes == expected
        captured.contentStreamProvider().newStream().bytes == expected
    }

    def "getMetaData returns null on 404 with null errorCode (HEAD-style response)"() {
        given:
        software.amazon.awssdk.services.s3.S3Client amazonS3Client = Stub()
        URI uri = new URI("s3://somehost/file.txt")
        S3Client s3Client = new S3Client(amazonS3Client, s3ConnectionProperties)
        // HEAD 404 with empty body: SDK v2 doesn't populate errorCode.
        S3Exception ex = (S3Exception) S3Exception.builder()
            .statusCode(404)
            .awsErrorDetails(AwsErrorDetails.builder().build())
            .build()
        amazonS3Client.headObject(_) >> { throw ex }

        expect:
        s3Client.getMetaData(uri) == null
    }

    def "getMetaData returns null on 404 with NoSuchKey errorCode"() {
        given:
        software.amazon.awssdk.services.s3.S3Client amazonS3Client = Stub()
        URI uri = new URI("s3://somehost/file.txt")
        S3Client s3Client = new S3Client(amazonS3Client, s3ConnectionProperties)
        S3Exception ex = (S3Exception) S3Exception.builder()
            .statusCode(404)
            .awsErrorDetails(AwsErrorDetails.builder().errorCode("NoSuchKey").build())
            .build()
        amazonS3Client.headObject(_) >> { throw ex }

        expect:
        s3Client.getMetaData(uri) == null
    }

    def "getMetaData does NOT swallow 404 with unrelated errorCode (e.g. NoSuchBucket)"() {
        given:
        software.amazon.awssdk.services.s3.S3Client amazonS3Client = Stub()
        URI uri = new URI("s3://somehost/file.txt")
        S3Client s3Client = new S3Client(amazonS3Client, s3ConnectionProperties)
        S3Exception ex = (S3Exception) S3Exception.builder()
            .statusCode(404)
            .awsErrorDetails(AwsErrorDetails.builder().errorCode("NoSuchBucket").build())
            .build()
        amazonS3Client.headObject(_) >> { throw ex }

        when:
        s3Client.getMetaData(uri)

        then:
        thrown(ResourceException)
    }

    def credentials() {
        def credentials = TestCredentialUtil.defaultAwsCredentials()
        credentials.setAccessKey("AKey")
        credentials.setSecretKey("ASecret")
        credentials
    }

    private static class NonMarkableInputStream extends ByteArrayInputStream {
        NonMarkableInputStream(byte[] buf) { super(buf) }
        @Override boolean markSupported() { false }
    }
}
