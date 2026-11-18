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

import org.gradle.api.resources.ResourceException
import org.gradle.internal.resource.ReadableContent
import org.gradle.internal.resource.local.FileReadableContent
import org.gradle.internal.resource.transport.aws.s3.S3Client
import org.gradle.internal.resource.transport.aws.s3.S3ConnectionProperties
import org.gradle.internal.resource.transport.aws.s3.S3RegionalResource
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestCredentialUtil
import org.junit.Rule
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.ListMultipartUploadsRequest
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import spock.lang.Ignore
import spock.lang.Specification

import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

/**
 * Exercises the S3 client against an S3-compatible server, configured with {@code org.gradle.s3.endpoint}.
 * Unlike the stub server, a real implementation checks signatures and checksums.
 *
 * To run it against RustFS, start a server with
 * {@code docker run --rm -p 9000:9000 -e RUSTFS_ACCESS_KEY=gradle -e RUSTFS_SECRET_KEY=gradle-secret rustfs/rustfs},
 * set {@code G_S3_COMPAT_ENDPOINT} to {@code http://localhost:9000}, {@code G_S3_COMPAT_ACCESS_KEY_ID} and {@code G_S3_COMPAT_SECRET_ACCESS_KEY},
 * add those names to {@code inheritedEnvVars()} in {@code gradlebuild.unittest-and-compile.gradle.kts},
 * since test tasks only see allowlisted environment variables, and remove the {@code @Ignore}.
 * Each test creates and deletes its own bucket.
 */
@Ignore("Needs an S3-compatible server, see the class documentation")
class S3CompatibleServerIntegrationTest extends Specification {
    static final int MIB = 1024 * 1024

    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    String bucket = "gradle-compat-test-${System.currentTimeMillis()}"
    S3Client s3Client

    def setup() {
        def credentials = TestCredentialUtil.defaultAwsCredentials()
        credentials.setAccessKey(env('G_S3_COMPAT_ACCESS_KEY_ID'))
        credentials.setSecretKey(env('G_S3_COMPAT_SECRET_ACCESS_KEY'))
        URI endpoint = new URI(env('G_S3_COMPAT_ENDPOINT'))
        S3ConnectionProperties properties = Stub() {
            getEndpoint() >> Optional.of(endpoint)
            getProxy() >> Optional.empty()
            getMaxErrorRetryCount() >> Optional.empty()
            // S3 rejects non-final parts below 5 MiB
            getPartSize() >> 5L * MIB
            getMultipartThreshold() >> 10L * MIB
        }
        s3Client = new S3Client(credentials, properties)
        sdkClient().createBucket(CreateBucketRequest.builder().bucket(bucket).build())
    }

    def cleanup() {
        def sdk = sdkClient()
        sdk.listObjectsV2(ListObjectsV2Request.builder().bucket(bucket).build()).contents().each { object ->
            sdk.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(object.key()).build())
        }
        sdk.deleteBucket(DeleteBucketRequest.builder().bucket(bucket).build())
    }

    def "single-part upload reads back intact and is listed"() {
        given:
        def file = randomFile("single.bin", MIB)
        def uri = new URI("s3://${bucket}/repo/single.bin")

        when:
        s3Client.put(new FileReadableContent(file), uri)

        then:
        s3Client.getMetaData(uri).contentLength() == file.length()
        sha256(download(uri)) == sha256(file.bytes)
        s3Client.listDirectChildren(new URI("s3://${bucket}/repo/")).contains('single.bin')
    }

    def "multipart upload reads back intact"() {
        given:
        def file = randomFile("multipart.bin", 12 * MIB)
        def uri = new URI("s3://${bucket}/repo/multipart.bin")

        when:
        s3Client.put(new FileReadableContent(file), uri)

        then:
        s3Client.getMetaData(uri).contentLength() == file.length()
        sha256(download(uri)) == sha256(file.bytes)
    }

    def "a multipart upload that fails part-way is aborted"() {
        given:
        def uri = new URI("s3://${bucket}/repo/aborted.bin")
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
        sdkClient().listMultipartUploads(ListMultipartUploadsRequest.builder().bucket(bucket).build()).uploads().isEmpty()
    }

    def "missing object is reported as missing"() {
        given:
        def uri = new URI("s3://${bucket}/repo/missing.bin")

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

    private software.amazon.awssdk.services.s3.S3Client sdkClient() {
        s3Client.clientFor(new S3RegionalResource(new URI("s3://${bucket}/any")))
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

    private byte[] download(URI uri) {
        def response = s3Client.getResource(uri)
        try {
            response.bytes
        } finally {
            response.close()
        }
    }
}
