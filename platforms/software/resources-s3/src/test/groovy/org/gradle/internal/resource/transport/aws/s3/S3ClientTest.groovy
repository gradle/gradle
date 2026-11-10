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
import software.amazon.awssdk.auth.credentials.AwsCredentialsProviderChain
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.awscore.exception.AwsErrorDetails
import software.amazon.awssdk.core.client.config.SdkClientOption
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.DelegatingS3Client
import software.amazon.awssdk.services.s3.internal.crossregion.S3CrossRegionSyncClient
import software.amazon.awssdk.services.s3.internal.endpoints.S3EndpointUtils
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse
import software.amazon.awssdk.services.s3.model.ObjectCannedACL
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response
import software.amazon.awssdk.services.s3.model.S3Object
import software.amazon.awssdk.http.SdkHttpConfigurationOption
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.UploadPartRequest
import software.amazon.awssdk.services.s3.model.UploadPartResponse
import org.gradle.api.resources.ResourceException
import org.gradle.internal.resource.HttpErrorStatusCodeException
import org.gradle.internal.logging.progress.ProgressLoggingInputStream
import org.gradle.internal.logging.progress.ProgressLoggingInputStreamListener
import org.gradle.internal.resource.ReadableContent
import org.gradle.internal.resource.local.ByteArrayReadableContent
import org.gradle.internal.resource.transport.http.HttpProxySettings
import org.gradle.util.TestCredentialUtil
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

import java.time.Duration


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
        client.put(new ByteArrayReadableContent(new byte[12]), uri)
        then:
        1 * amazonS3Client.putObject(*_) >> { args ->
            PutObjectRequest putObjectRequest = args[0]
            assert putObjectRequest.bucket == 'localhost'
            assert putObjectRequest.key == 'maven/snapshot/myFile.txt'
            assert putObjectRequest.acl == ObjectCannedACL.BUCKET_OWNER_FULL_CONTROL.toString()
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
        client.put(new ByteArrayReadableContent(new byte[12]), uri)
        then:
        1 * amazonS3Client.createMultipartUpload(*_) >> { args ->
            CreateMultipartUploadRequest createMultipartUpload = args[0]
            assert createMultipartUpload.bucket == bucketName
            assert createMultipartUpload.key == objectKey
            assert createMultipartUpload.acl == ObjectCannedACL.BUCKET_OWNER_FULL_CONTROL.toString()
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

    def "lists the children from every page of a truncated listing"() {
        given:
        software.amazon.awssdk.services.s3.S3Client amazonS3Client = Mock()
        S3Client s3Client = new S3Client(amazonS3Client, s3ConnectionProperties)
        amazonS3Client.listObjectsV2Paginator(_) >> { args -> new ListObjectsV2Iterable(amazonS3Client, args[0] as ListObjectsV2Request) }

        when:
        def children = s3Client.listDirectChildren(new URI("s3://mybucket/maven/release/"))

        then:
        2 * amazonS3Client.listObjectsV2(_) >>> [
            ListObjectsV2Response.builder()
                .contents(S3Object.builder().key("maven/release/a.jar").build())
                .isTruncated(true)
                .nextContinuationToken("next")
                .build(),
            ListObjectsV2Response.builder()
                .contents(S3Object.builder().key("maven/release/b.jar").build())
                .isTruncated(false)
                .build()
        ]

        and:
        children == ["a.jar", "b.jar"]
    }

    def "should apply endpoint override with path style access"() {
        setup:
        Optional<URI> someEndpoint = Optional.of(new URI("http://someEndpoint"))
        S3ConnectionProperties s3Properties = Stub()
        s3Properties.getEndpoint() >> someEndpoint

        when:
        S3Client s3Client = new S3Client(credentials(), s3Properties)
        def amazonS3Client = unwrapped(s3Client.clientFor(someResource()))
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
        def amazonS3Client = unwrapped(s3Client.clientFor(someResource()))
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
        def amazonS3Client = unwrapped(s3Client.clientFor(someResource()))
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
        def amazonS3Client = unwrapped(s3Client.clientFor(someResource()))
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

    def "reports the status code S3 answered with"() {
        software.amazon.awssdk.services.s3.S3Client amazonS3Client = Mock()
        URI uri = new URI("https://somehost/file.txt")
        S3Client s3Client = new S3Client(amazonS3Client, s3ConnectionProperties)
        S3Exception amazonS3Exception = S3Exception.builder()
            .statusCode(403)
            .awsErrorDetails(AwsErrorDetails.builder().errorCode("AccessDenied").errorMessage("Access Denied").build())
            .build()
        amazonS3Client.getObject(_) >> { throw amazonS3Exception }

        when:
        s3Client.getResource(uri)

        then:
        def ex = thrown(HttpErrorStatusCodeException)
        ex.statusCode == 403
        ex.cause == amazonS3Exception
        ex.message == "Could not get resource 'https://somehost/file.txt'."
    }

    def "reports the status code when listing fails"() {
        software.amazon.awssdk.services.s3.S3Client amazonS3Client = Mock()
        S3Client s3Client = new S3Client(amazonS3Client, s3ConnectionProperties)
        amazonS3Client.listObjectsV2Paginator(_) >> { throw S3Exception.builder().statusCode(500).build() }

        when:
        s3Client.listDirectChildren(new URI("s3://mybucket/maven/release/"))

        then:
        def ex = thrown(HttpErrorStatusCodeException)
        ex.statusCode == 500
    }

    def "a missing object is not reported as a failure"() {
        software.amazon.awssdk.services.s3.S3Client amazonS3Client = Mock()
        URI uri = new URI("https://somehost/file.txt")
        S3Client s3Client = new S3Client(amazonS3Client, s3ConnectionProperties)
        amazonS3Client.getObject(_) >> {
            throw S3Exception.builder()
                .statusCode(404)
                .awsErrorDetails(AwsErrorDetails.builder().errorCode("NoSuchKey").build())
                .build()
        }

        expect:
        s3Client.getResource(uri) == null
    }

    def "should include uri when upload fails"() {
        software.amazon.awssdk.services.s3.S3Client amazonS3Client = Stub()
        URI uri = new URI("https://somehost/file.txt")
        S3Client s3Client = new S3Client(amazonS3Client, s3ConnectionProperties)

        S3Exception amazonS3Exception = S3Exception.builder()
            .awsErrorDetails(AwsErrorDetails.builder().errorMessage("test exception").build())
            .build()
        amazonS3Client.putObject(*_) >> { throw amazonS3Exception }

        when:
        s3Client.put(new ByteArrayReadableContent(new byte[0]), uri)
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
        def amazonS3Client = unwrapped(s3Client.clientFor(someResource()))
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
        def amazonS3Client = unwrapped(s3Client.clientFor(someResource()))

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
        def amazonS3Client = unwrapped(s3Client.clientFor(someResource()))

        then:
        // Guards against a regression to null/DefaultCredentialsProvider, which would leak IMDS
        // lookups and break anonymous S3 access to public buckets.
        amazonS3Client.serviceClientConfiguration().credentialsProvider() instanceof AnonymousCredentialsProvider
    }

    @RestoreSystemProperties
    def "uses the default provider chain when constructed without credentials"() {
        given:
        System.setProperty("aws.accessKeyId", "fromChainKey")
        System.setProperty("aws.secretAccessKey", "fromChainSecret")
        S3ConnectionProperties s3Properties = Stub()
        s3Properties.getEndpoint() >> Optional.empty()
        s3Properties.getProxy() >> Optional.empty()
        s3Properties.getMaxErrorRetryCount() >> Optional.empty()
        S3Client s3Client = new S3Client(s3Properties)

        when:
        def provider = s3Client.clientFor(someResource()).serviceClientConfiguration().credentialsProvider()

        then:
        provider.resolveCredentials().accessKeyId() == "fromChainKey"
    }

    def "falls back to anonymous access after the default provider chain"() {
        given:
        S3Client s3Client = new S3Client(s3ConnectionProperties)

        expect:
        s3Client.credentialsProvider instanceof AwsCredentialsProviderChain
        s3Client.credentialsProvider.credentialsProviders*.class == [DefaultCredentialsProvider, AnonymousCredentialsProvider]
    }

    def "single-part body reopens the content each time the SDK re-reads it"() {
        given:
        software.amazon.awssdk.services.s3.S3Client amazonS3Client = Mock()
        S3Client s3Client = new S3Client(amazonS3Client, s3ConnectionProperties)
        URI uri = new URI("s3://localhost/foo.txt")
        byte[] expected = 'hello world'.bytes
        byte[] firstRead = null
        byte[] secondRead = null

        when:
        s3Client.put(new ByteArrayReadableContent(expected), uri)

        then:
        1 * amazonS3Client.putObject(*_) >> { args ->
            def provider = (args[1] as RequestBody).contentStreamProvider()
            firstRead = provider.newStream().bytes
            secondRead = provider.newStream().bytes
            null
        }

        and:
        firstRead == expected
        secondRead == expected
    }

    def "multi-part bodies cover consecutive ranges and stay re-readable"() {
        given:
        software.amazon.awssdk.services.s3.S3Client amazonS3Client = Mock()
        S3ConnectionProperties s3Properties = Stub()
        s3Properties.getEndpoint() >> Optional.empty()
        s3Properties.getPartSize() >> 7
        s3Properties.getMultipartThreshold() >> 10
        S3Client s3Client = new S3Client(amazonS3Client, s3Properties)
        amazonS3Client.createMultipartUpload(_) >> CreateMultipartUploadResponse.builder().uploadId('1').build()
        List<String> parts = []

        when:
        s3Client.put(new ByteArrayReadableContent('hello world!'.bytes), new URI("s3://localhost/foo.txt"))

        then:
        2 * amazonS3Client.uploadPart(*_) >> { args ->
            def provider = (args[1] as RequestBody).contentStreamProvider()
            provider.newStream().bytes
            parts << new String(provider.newStream().bytes)
            UploadPartResponse.builder().eTag('eTag').build()
        }

        and:
        parts == ['hello w', 'orld!']
    }

    def "follows S3 redirects across regions on AWS"() {
        given:
        S3ConnectionProperties s3Properties = Stub()
        s3Properties.getEndpoint() >> Optional.empty()
        s3Properties.getProxy() >> Optional.empty()
        s3Properties.getMaxErrorRetryCount() >> Optional.empty()

        expect:
        new S3Client(credentials(), s3Properties).clientFor(someResource()) instanceof S3CrossRegionSyncClient
    }

    def "does not follow redirects across regions on a custom endpoint"() {
        given:
        S3ConnectionProperties s3Properties = Stub()
        s3Properties.getEndpoint() >> Optional.of(new URI("http://someEndpoint"))
        s3Properties.getProxy() >> Optional.empty()
        s3Properties.getMaxErrorRetryCount() >> Optional.empty()

        expect:
        !(new S3Client(credentials(), s3Properties).clientFor(someResource()) instanceof S3CrossRegionSyncClient)
    }

    def "signs requests to a custom endpoint for the region in its host, like SDK v1"() {
        expect:
        S3Client.signingRegion(new URI("https://${host}")) == Region.of(region)

        where:
        host                                     | region
        "s3.eu-west-1.amazonaws.com"             | "eu-west-1"
        "s3-eu-west-1.amazonaws.com"             | "eu-west-1"
        "s3.amazonaws.com"                       | "us-east-1"
        "s3-external-1.amazonaws.com"            | "us-east-1"
        "s3.cn-north-1.amazonaws.com.cn"         | "cn-north-1"
        "s3.dualstack.eu-west-1.amazonaws.com"   | "eu-west-1"
        "s3-fips.us-east-2.amazonaws.com"        | "us-east-2"
        "bucket.s3.eu-west-2.amazonaws.com"      | "eu-west-2"
        "s3.eu-central-1.wasabisys.com"          | "eu-central-1"
        "s3.us-west-002.backblazeb2.com"         | "us-west-002"
        "s3.fr-par.scw.cloud"                    | "fr-par"
        "s3.example.com"                         | "example"
        "localhost"                              | "us-east-1"
        "127.0.0.1"                              | "us-east-1"
        "minio.example.com"                      | "us-east-1"
        "storage.googleapis.com"                 | "us-east-1"
        "nyc3.digitaloceanspaces.com"            | "us-east-1"
        "abc123.r2.cloudflarestorage.com"        | "us-east-1"
    }

    def "builds the client for a custom endpoint with the endpoint's region"() {
        given:
        S3ConnectionProperties s3Properties = Stub()
        s3Properties.getEndpoint() >> Optional.of(new URI("https://s3.eu-central-1.wasabisys.com"))
        s3Properties.getProxy() >> Optional.empty()
        s3Properties.getMaxErrorRetryCount() >> Optional.empty()

        expect:
        new S3Client(credentials(), s3Properties).clientFor(someResource()).serviceClientConfiguration().region() == Region.EU_CENTRAL_1
    }

    @RestoreSystemProperties
    def "ignores the SDK's configured endpoint URLs"() {
        given:
        def configFile = File.createTempFile("aws-config", ".ini")
        configFile.deleteOnExit()
        configFile.text = "[default]\nendpoint_url = http://from-profile.invalid\n"
        System.setProperty("aws.configFile", configFile.absolutePath)
        System.setProperty("aws.endpointUrlS3", "http://from-system-property.invalid")
        S3ConnectionProperties s3Properties = Stub()
        s3Properties.getEndpoint() >> Optional.empty()
        s3Properties.getProxy() >> Optional.empty()
        s3Properties.getMaxErrorRetryCount() >> Optional.empty()

        when:
        def plainSdkClient = software.amazon.awssdk.services.s3.S3Client.builder()
            .region(Region.US_EAST_1)
            .credentialsProvider(AnonymousCredentialsProvider.create())
            .build()
        def gradleClient = new S3Client(credentials(), s3Properties).clientFor(someResource())

        then:
        // Shows that the settings take effect on a client that does not opt out
        plainSdkClient.serviceClientConfiguration().endpointOverride().isPresent()
        !gradleClient.serviceClientConfiguration().endpointOverride().isPresent()

        cleanup:
        plainSdkClient?.close()
    }

    def "uses SDK v1's HTTP timeouts"() {
        given:
        S3ConnectionProperties s3Properties = Stub()
        s3Properties.getEndpoint() >> Optional.empty()
        s3Properties.getProxy() >> Optional.empty()
        s3Properties.getMaxErrorRetryCount() >> Optional.empty()

        when:
        def httpClient = unwrapped(new S3Client(credentials(), s3Properties).clientFor(someResource())).clientConfiguration.option(SdkClientOption.SYNC_HTTP_CLIENT)
        def options = httpClient.resolvedOptions

        then:
        options.get(SdkHttpConfigurationOption.CONNECTION_TIMEOUT) == Duration.ofSeconds(10)
        options.get(SdkHttpConfigurationOption.READ_TIMEOUT) == Duration.ofSeconds(50)
    }

    def "aborts a multipart upload when reading the content fails"() {
        given:
        software.amazon.awssdk.services.s3.S3Client amazonS3Client = Mock()
        S3ConnectionProperties s3Properties = Stub()
        s3Properties.getEndpoint() >> Optional.empty()
        s3Properties.getPartSize() >> 7
        s3Properties.getMultipartThreshold() >> 10
        S3Client s3Client = new S3Client(amazonS3Client, s3Properties)
        amazonS3Client.createMultipartUpload(_) >> CreateMultipartUploadResponse.builder().uploadId('1').build()
        def failure = new ResourceException("the content went away")

        when:
        s3Client.put(new ByteArrayReadableContent(new byte[12]), new URI("s3://localhost/foo.txt"))

        then:
        1 * amazonS3Client.uploadPart(*_) >> { throw failure }
        1 * amazonS3Client.abortMultipartUpload(_)

        and:
        def ex = thrown(ResourceException)
        ex.is(failure)
    }

    def "multi-part upload reads each part once through progress logging"() {
        given:
        software.amazon.awssdk.services.s3.S3Client amazonS3Client = Mock()
        S3ConnectionProperties s3Properties = Stub()
        s3Properties.getEndpoint() >> Optional.empty()
        s3Properties.getPartSize() >> 7
        s3Properties.getMultipartThreshold() >> 10
        S3Client s3Client = new S3Client(amazonS3Client, s3Properties)
        amazonS3Client.createMultipartUpload(_) >> CreateMultipartUploadResponse.builder().uploadId('1').build()
        long processed = 0
        def content = new ReadableContent() {
            @Override
            InputStream open() {
                new ProgressLoggingInputStream(new ByteArrayInputStream(new byte[20]), { processed += it } as ProgressLoggingInputStreamListener)
            }

            @Override
            long getContentLength() { 20 }
        }

        when:
        s3Client.put(content, new URI("s3://localhost/foo.txt"))

        then:
        3 * amazonS3Client.uploadPart(*_) >> { args ->
            (args[1] as RequestBody).contentStreamProvider().newStream().bytes
            UploadPartResponse.builder().eTag('eTag').build()
        }

        and:
        // Reaching a later part must skip the earlier ones, not read them
        processed == 20
    }

    def "reuses one client per region"() {
        given:
        S3ConnectionProperties s3Properties = Stub()
        s3Properties.getEndpoint() >> Optional.empty()
        s3Properties.getProxy() >> Optional.empty()
        s3Properties.getMaxErrorRetryCount() >> Optional.empty()
        S3Client s3Client = new S3Client(credentials(), s3Properties)
        def euWest = new URI("s3://mybucket.s3.eu-west-1.amazonaws.com/file.txt")
        def usWest = new URI("s3://mybucket.s3.us-west-2.amazonaws.com/file.txt")

        when:
        def first = s3Client.clientFor(new S3RegionalResource(euWest))
        def second = s3Client.clientFor(new S3RegionalResource(euWest))
        def other = s3Client.clientFor(new S3RegionalResource(usWest))

        then:
        first.is(second)
        !first.is(other)
        first.serviceClientConfiguration().region() == Region.EU_WEST_1
        other.serviceClientConfiguration().region() == Region.US_WEST_2
    }

    def "tolerates exceptions without error details"() {
        given:
        software.amazon.awssdk.services.s3.S3Client amazonS3Client = Stub()
        S3Client s3Client = new S3Client(amazonS3Client, s3ConnectionProperties)
        URI uri = new URI("s3://somehost/file.txt")
        amazonS3Client.headObject(_) >> { throw S3Exception.builder().statusCode(status).build() }
        amazonS3Client.getObject(_) >> { throw noSuchKey() }

        when:
        def result = null
        Exception failure = null
        try {
            result = s3Client.getMetaData(uri)
        } catch (HttpErrorStatusCodeException e) {
            failure = e
        }

        then:
        result == null
        (failure != null) == fails

        where:
        status | fails
        404    | false
        500    | true
        301    | true
    }

    def "getMetaData confirms a HEAD 404 with a GET, since the SDK reports every HEAD 404 as NoSuchKey"() {
        given:
        software.amazon.awssdk.services.s3.S3Client amazonS3Client = Mock()
        URI uri = new URI("s3://somehost/file.txt")
        S3Client s3Client = new S3Client(amazonS3Client, s3ConnectionProperties)

        when:
        def result = null
        Exception failure = null
        try {
            result = s3Client.getMetaData(uri)
        } catch (HttpErrorStatusCodeException e) {
            failure = e
        }

        then:
        1 * amazonS3Client.headObject(_) >> { throw noSuchKey() }
        1 * amazonS3Client.getObject(_) >> { throw S3Exception.builder().statusCode(404).awsErrorDetails(AwsErrorDetails.builder().errorCode(getErrorCode).build()).build() }

        and:
        result == null
        (failure != null) == fails

        where:
        getErrorCode   | fails
        "NoSuchKey"    | false
        "NoSuchBucket" | true
        null           | false
    }

    def "getMetaData does not repeat a HEAD failure other than 404"() {
        given:
        software.amazon.awssdk.services.s3.S3Client amazonS3Client = Mock()
        S3Client s3Client = new S3Client(amazonS3Client, s3ConnectionProperties)

        when:
        s3Client.getMetaData(new URI("s3://somehost/file.txt"))

        then:
        1 * amazonS3Client.headObject(_) >> { throw S3Exception.builder().statusCode(403).build() }
        0 * amazonS3Client.getObject(_)

        and:
        def ex = thrown(HttpErrorStatusCodeException)
        ex.statusCode == 403
    }

    private static S3RegionalResource someResource() {
        new S3RegionalResource(new URI("s3://mybucket/file.txt"))
    }

    private static S3Exception noSuchKey() {
        S3Exception.builder().statusCode(404).awsErrorDetails(AwsErrorDetails.builder().errorCode("NoSuchKey").build()).build()
    }

    private static def unwrapped(client) {
        client instanceof DelegatingS3Client ? unwrapped(client.delegate()) : client
    }

    def credentials() {
        def credentials = TestCredentialUtil.defaultAwsCredentials()
        credentials.setAccessKey("AKey")
        credentials.setSecretKey("ASecret")
        credentials
    }
}
