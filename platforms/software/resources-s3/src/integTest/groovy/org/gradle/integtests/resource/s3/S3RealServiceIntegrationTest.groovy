/*
 * Copyright 2026 the original author or authors.
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

import org.gradle.api.credentials.AwsCredentials
import org.gradle.api.resources.ResourceException
import org.gradle.internal.resource.ReadableContent
import org.gradle.internal.resource.local.FileReadableContent
import org.gradle.internal.resource.transport.aws.s3.S3Client
import org.gradle.internal.resource.transport.aws.s3.S3ConnectionProperties
import org.gradle.internal.resource.transport.aws.s3.S3RegionalResource
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestCredentialUtil
import org.junit.Rule
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.ListMultipartUploadsRequest
import spock.lang.Ignore
import spock.lang.Specification

import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

/**
 * Exercises the S3 client against real AWS S3, which the stub server cannot stand in for:
 * signing, region redirects, custom endpoints, session credentials, multipart uploads and S3's error responses.
 *
 * To run it, set {@code G_S3_BUCKET}, {@code G_S3_REGION} (the bucket's region), {@code G_AWS_ACCESS_KEY_ID} and {@code G_AWS_SECRET_ACCESS_KEY},
 * add those names to {@code inheritedEnvVars()} in {@code gradlebuild.unittest-and-compile.gradle.kts},
 * since test tasks only see allowlisted environment variables, and remove the {@code @Ignore}.
 * Pass {@code -DenableTestDistribution=false} so that the credentials stay on your machine.
 * Use a bucket outside {@code us-east-1} to exercise region redirects.
 * The credentials need {@code s3:ListBucket} and {@code s3:ListBucketMultipartUploads} on the bucket and
 * {@code s3:GetObject}, {@code s3:PutObject}, {@code s3:PutObjectAcl}, {@code s3:DeleteObject} and {@code s3:AbortMultipartUpload} on its objects.
 * The session credentials test gets temporary credentials for the same user from STS.
 */
@Ignore("Needs real AWS credentials, see the class documentation")
class S3RealServiceIntegrationTest extends Specification {
    static final int MIB = 1024 * 1024

    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    String bucket = env('G_S3_BUCKET')
    String folder = "gradle-real-test-${System.currentTimeMillis()}"
    List<URI> uploaded = []
    S3Client s3Client

    def setup() {
        s3Client = new S3Client(credentials(), connectionProperties(Optional.empty()))
    }

    def cleanup() {
        uploaded.each { uri ->
            s3Client.clientFor(new S3RegionalResource(uri)).deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(uri.path.substring(1)).build())
        }
    }

    def "multipart upload of #sizeMiB MiB reads back intact through a region-less URL"() {
        given:
        def file = randomFile("multipart-${sizeMiB}.bin", sizeMiB * MIB)
        def uri = new URI("s3://${bucket}/${folder}/multipart-${sizeMiB}.bin")
        uploaded << uri

        when:
        s3Client.put(new FileReadableContent(file), uri)
        def metadata = s3Client.getMetaData(uri)

        then:
        metadata.contentLength() == file.length()
        metadata.eTag().contains("-${expectedParts}")
        sha256(download(s3Client, uri)) == sha256(file.bytes)

        where:
        sizeMiB | expectedParts
        12      | 3
        15      | 3
    }

    def "single-part upload reads back intact and is listed"() {
        given:
        def file = randomFile("single.bin", MIB)
        def uri = new URI("s3://${bucket}/${folder}/single.bin")
        uploaded << uri

        when:
        s3Client.put(new FileReadableContent(file), uri)

        then:
        !s3Client.getMetaData(uri).eTag().contains('-')
        sha256(download(s3Client, uri)) == sha256(file.bytes)
        s3Client.listDirectChildren(new URI("s3://${bucket}/${folder}/")).contains('single.bin')
    }

    def "requests to a custom endpoint are signed for the endpoint's region"() {
        given:
        // AWS rejects a request signed for a region other than the endpoint's
        def endpointClient = new S3Client(credentials(), connectionProperties(Optional.of(new URI("https://s3.${env('G_S3_REGION')}.amazonaws.com"))))
        def file = randomFile("endpoint.bin", MIB)
        def uri = new URI("s3://${bucket}/${folder}/endpoint.bin")
        uploaded << uri

        when:
        endpointClient.put(new FileReadableContent(file), uri)

        then:
        sha256(download(endpointClient, uri)) == sha256(file.bytes)
        endpointClient.getMetaData(new URI("s3://${bucket}/${folder}/missing.bin")) == null
    }

    def "a multipart upload that fails part-way is aborted"() {
        given:
        def uri = new URI("s3://${bucket}/${folder}/aborted.bin")
        def opens = new AtomicInteger()
        def content = new ReadableContent() {
            @Override
            InputStream open() {
                // The first part reads the content, the second fails
                if (opens.incrementAndGet() > 1) {
                    throw new ResourceException("Simulated read failure")
                }
                new ByteArrayInputStream(new byte[12 * MIB])
            }

            @Override
            long getContentLength() {
                12L * MIB
            }
        }

        when:
        s3Client.put(content, uri)

        then:
        thrown(ResourceException)

        and:
        pendingUploads(uri).isEmpty()
    }

    def "session credentials can upload and download"() {
        given:
        def temporary = temporaryCredentials()
        def credentials = TestCredentialUtil.defaultAwsCredentials()
        credentials.setAccessKey(temporary.accessKeyId())
        credentials.setSecretKey(temporary.secretAccessKey())
        credentials.setSessionToken(temporary.sessionToken())
        def sessionClient = new S3Client(credentials, connectionProperties(Optional.empty()))
        def file = randomFile("session.bin", MIB)
        def uri = new URI("s3://${bucket}/${folder}/session.bin")
        uploaded << uri

        when:
        sessionClient.put(new FileReadableContent(file), uri)

        then:
        sha256(download(sessionClient, uri)) == sha256(file.bytes)
    }

    def "missing object is reported as missing"() {
        given:
        def uri = new URI("s3://${bucket}/${folder}/missing.bin")

        expect:
        s3Client.getMetaData(uri) == null
        s3Client.getResource(uri) == null
    }

    def "metadata request for a bucket that does not exist is a failure, not a missing resource"() {
        when:
        s3Client.getMetaData(new URI("s3://gradle-no-such-bucket-${UUID.randomUUID()}/file.txt"))

        then:
        thrown(ResourceException)
    }

    def "download from a bucket that does not exist is a failure, not a missing resource"() {
        when:
        s3Client.getResource(new URI("s3://gradle-no-such-bucket-${UUID.randomUUID()}/file.txt"))

        then:
        thrown(ResourceException)
    }

    private static String env(String name) {
        String value = System.getenv(name)
        if (!value) {
            throw new IllegalStateException("Set the ${name} environment variable, see the class documentation")
        }
        value
    }

    private List<?> pendingUploads(URI uri) {
        String key = uri.path.substring(1)
        s3Client.clientFor(new S3RegionalResource(uri))
            .listMultipartUploads(ListMultipartUploadsRequest.builder().bucket(bucket).prefix(key).build())
            .uploads()
    }

    // The sts module is only on the runtime classpath, so it is used dynamically
    private static temporaryCredentials() {
        def basic = Class.forName('software.amazon.awssdk.auth.credentials.AwsBasicCredentials').create(env('G_AWS_ACCESS_KEY_ID'), env('G_AWS_SECRET_ACCESS_KEY'))
        def sts = Class.forName('software.amazon.awssdk.services.sts.StsClient').builder()
            .region(Region.of(env('G_S3_REGION')))
            .credentialsProvider(Class.forName('software.amazon.awssdk.auth.credentials.StaticCredentialsProvider').create(basic))
            .build()
        try {
            sts.getSessionToken().credentials()
        } finally {
            sts.close()
        }
    }

    private static AwsCredentials credentials() {
        def credentials = TestCredentialUtil.defaultAwsCredentials()
        credentials.setAccessKey(env('G_AWS_ACCESS_KEY_ID'))
        credentials.setSecretKey(env('G_AWS_SECRET_ACCESS_KEY'))
        credentials
    }

    private S3ConnectionProperties connectionProperties(Optional<URI> endpoint) {
        S3ConnectionProperties properties = Stub() {
            getEndpoint() >> endpoint
            getProxy() >> Optional.empty()
            getMaxErrorRetryCount() >> Optional.empty()
            // S3 rejects non-final parts below 5 MiB
            getPartSize() >> 5L * MIB
            getMultipartThreshold() >> 10L * MIB
        }
        properties
    }

    private File randomFile(String name, int size) {
        def bytes = new byte[size]
        new Random().nextBytes(bytes)
        def file = temporaryFolder.createFile(name)
        file.bytes = bytes
        file
    }

    private static String sha256(byte[] bytes) {
        MessageDigest.getInstance('SHA-256').digest(bytes).encodeHex().toString()
    }

    private static byte[] download(S3Client client, URI uri) {
        def response = client.getResource(uri)
        try {
            response.bytes
        } finally {
            response.close()
        }
    }
}
