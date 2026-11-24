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
import org.junit.Assume
import org.junit.Rule
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.model.CreateBucketRequest
import software.amazon.awssdk.services.s3.model.DeleteBucketRequest
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.ListMultipartUploadsRequest
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import spock.lang.Ignore
import spock.lang.Specification

import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import java.security.KeyStore
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

/**
 * Exercises the S3 client against AWS Emulator (https://github.com/aws/aws-emulator), configured with {@code org.gradle.s3.endpoint}.
 * Unlike the stub server, the emulator checks SigV4 signatures, and it emulates STS, so this needs no AWS account or credentials.
 * It can't stand in for region redirects or custom-endpoint regions, because an emulator serves a single Region.
 *
 * <p>The test needs an emulator that you start in Docker, so it is ignored. To run it:</p>
 * <ol>
 * <li>Build the image. AWS doesn't publish one, so create this {@code Dockerfile} in an empty directory and run
 * {@code docker build -t aws-emulator .} there. On Intel machines, use {@code aws-emulator-x86_64.rpm} instead of {@code aws-emulator-aarch64.rpm}.
 * <pre>
 * FROM public.ecr.aws/amazonlinux/amazonlinux:2023-minimal
 * ADD https://downloads.aws.com/aws-emulator/packages/aws-emulator/LTS/latest/aws-emulator-aarch64.rpm /tmp/aws-emulator.rpm
 * RUN rpm -i /tmp/aws-emulator.rpm &amp;&amp; rm /tmp/aws-emulator.rpm \
 *  &amp;&amp; mkdir -p /home/emulator &amp;&amp; chown 1000 /home/emulator
 * USER 1000
 * ENV HOME=/home/emulator
 * EXPOSE 9297
 * ENTRYPOINT ["aws-emulator", "-b", "0.0.0.0"]
 * </pre></li>
 * <li>Start the emulator on port 9297. It writes its credentials and trust store to {@code ~/.aws/emulator/9297}, where the test reads them.
 * <pre>
 * mkdir -p ~/.aws/emulator
 * docker run --rm -d --name gradle-aws-emulator --memory=2g -p 127.0.0.1:9297:9297 \
 *     -e HOME="$HOME" -v "$HOME/.aws/emulator:$HOME/.aws/emulator" aws-emulator -p 9297 --telemetry off
 * </pre></li>
 * <li>Remove the {@code @Ignore}, and run {@code ./gradlew :resources-s3:forkingIntegTest --tests '*S3AwsEmulatorIntegrationTest'}.
 * No credentials or environment variables are needed. Each test is skipped when no emulator answers on port 9297.</li>
 * <li>Stop the emulator with {@code docker stop gradle-aws-emulator}. It deletes its files in {@code ~/.aws/emulator/9297} when it stops.</li>
 * </ol>
 * <p>See the AWS Emulator user guide, https://docs.aws.amazon.com/emulator/latest/userguide/getting-started-docker.html, for details.</p>
 */
@Ignore("Needs AWS Emulator running in Docker, see the class documentation")
class S3AwsEmulatorIntegrationTest extends Specification {
    static final int MIB = 1024 * 1024
    static final int PORT = 9297
    static final File EMULATOR_DIR = new File(System.getProperty("user.home"), ".aws/emulator/${PORT}")
    static final URI ENDPOINT = new URI("https://127.0.0.1:${PORT}")

    @Rule
    final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider(getClass())

    String bucket = "gradle-emulator-test-${System.currentTimeMillis()}"
    Map<String, String> savedProperties = [:]
    S3Client s3Client

    def setup() {
        Assume.assumeTrue("No AWS Emulator serves port ${PORT}", emulatorIsRunning())
        // The emulator serves HTTPS with its own certificate, which the SDK's HTTP client must trust
        setSystemProperty("javax.net.ssl.trustStore", trustStore().absolutePath)
        setSystemProperty("javax.net.ssl.trustStoreType", "PKCS12")
        s3Client = clientWith(emulatorCredentials()['aws_access_key_id'], emulatorCredentials()['aws_secret_access_key'], null)
        sdkClient().createBucket(CreateBucketRequest.builder().bucket(bucket).build())
    }

    def cleanup() {
        if (s3Client != null) {
            def sdk = sdkClient()
            sdk.listObjectsV2(ListObjectsV2Request.builder().bucket(bucket).build()).contents().each { object ->
                sdk.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(object.key()).build())
            }
            sdk.deleteBucket(DeleteBucketRequest.builder().bucket(bucket).build())
        }
        savedProperties.each { name, value ->
            value == null ? System.clearProperty(name) : System.setProperty(name, value)
        }
    }

    def "single-part upload reads back intact and is listed"() {
        given:
        def file = randomFile("single.bin", MIB)
        def uri = new URI("s3://${bucket}/repo/single.bin")

        when:
        s3Client.put(new FileReadableContent(file), uri)

        then:
        s3Client.getMetaData(uri).contentLength() == file.length()
        sha256(download(s3Client, uri)) == sha256(file.bytes)
        s3Client.listDirectChildren(new URI("s3://${bucket}/repo/")).contains('single.bin')
    }

    def "multipart upload reads back intact"() {
        given:
        def file = randomFile("multipart.bin", 12 * MIB)
        def uri = new URI("s3://${bucket}/repo/multipart.bin")

        when:
        s3Client.put(new FileReadableContent(file), uri)
        def metadata = s3Client.getMetaData(uri)

        then:
        metadata.contentLength() == file.length()
        metadata.eTag().contains("-3")
        sha256(download(s3Client, uri)) == sha256(file.bytes)
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

    def "session credentials from STS can upload and download"() {
        given:
        def temporary = temporaryCredentials()
        def sessionClient = clientWith(temporary.accessKeyId(), temporary.secretAccessKey(), temporary.sessionToken())
        def file = randomFile("session.bin", MIB)
        def uri = new URI("s3://${bucket}/repo/session.bin")

        when:
        sessionClient.put(new FileReadableContent(file), uri)

        then:
        sha256(download(sessionClient, uri)) == sha256(file.bytes)
    }

    def "the default credential chain used by AwsImAuthentication can upload and download"() {
        given:
        setSystemProperty("aws.sharedCredentialsFile", new File(EMULATOR_DIR, "emulator_credentials").absolutePath)
        def chainClient = new S3Client(connectionProperties())
        def file = randomFile("chain.bin", MIB)
        def uri = new URI("s3://${bucket}/repo/chain.bin")

        when:
        chainClient.put(new FileReadableContent(file), uri)

        then:
        sha256(download(chainClient, uri)) == sha256(file.bytes)
    }

    def "an unsigned upload is rejected"() {
        given:
        def anonymousClient = new S3Client((org.gradle.api.credentials.AwsCredentials) null, connectionProperties())

        when:
        anonymousClient.put(new FileReadableContent(randomFile("anonymous.bin", 1024)), new URI("s3://${bucket}/repo/anonymous.bin"))

        then:
        thrown(ResourceException)
    }

    def "an upload signed with the wrong secret is rejected"() {
        given:
        def wrongSecretClient = clientWith(emulatorCredentials()['aws_access_key_id'], "not-the-secret", null)

        when:
        wrongSecretClient.put(new FileReadableContent(randomFile("wrong.bin", 1024)), new URI("s3://${bucket}/repo/wrong.bin"))

        then:
        thrown(ResourceException)
    }

    private static boolean emulatorIsRunning() {
        if (!trustStore().file || !new File(EMULATOR_DIR, "emulator_credentials").file) {
            return false
        }
        try {
            // A crashed emulator leaves its files behind, so check that it answers
            def keyStore = KeyStore.getInstance("PKCS12")
            trustStore().withInputStream { keyStore.load(it, new char[0]) }
            def trustManagers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            trustManagers.init(keyStore)
            def sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustManagers.trustManagers, null)
            def connection = (HttpsURLConnection) new URL("${ENDPOINT}/__admin/health").openConnection()
            connection.SSLSocketFactory = sslContext.socketFactory
            connection.connectTimeout = 2000
            connection.readTimeout = 2000
            return connection.responseCode == 200
        } catch (IOException ignored) {
            return false
        }
    }

    private static File trustStore() {
        new File(EMULATOR_DIR, "truststore.p12")
    }

    private static Map<String, String> emulatorCredentials() {
        new File(EMULATOR_DIR, "emulator_credentials").readLines()
            .findAll { it.contains('=') }
            .collectEntries { line -> def parts = line.split('=', 2); [(parts[0].trim()): parts[1].trim()] }
    }

    private void setSystemProperty(String name, String value) {
        if (!savedProperties.containsKey(name)) {
            savedProperties[name] = System.getProperty(name)
        }
        System.setProperty(name, value)
    }

    private S3Client clientWith(String accessKey, String secretKey, String sessionToken) {
        def credentials = TestCredentialUtil.defaultAwsCredentials()
        credentials.setAccessKey(accessKey)
        credentials.setSecretKey(secretKey)
        credentials.setSessionToken(sessionToken)
        new S3Client(credentials, connectionProperties())
    }

    private S3ConnectionProperties connectionProperties() {
        S3ConnectionProperties properties = Stub() {
            getEndpoint() >> Optional.of(ENDPOINT)
            getProxy() >> Optional.empty()
            getMaxErrorRetryCount() >> Optional.empty()
            // S3 rejects non-final parts below 5 MiB
            getPartSize() >> 5L * MIB
            getMultipartThreshold() >> 10L * MIB
        }
        properties
    }

    private software.amazon.awssdk.services.s3.S3Client sdkClient() {
        s3Client.clientFor(new S3RegionalResource(new URI("s3://${bucket}/any")))
    }

    // The sts module is only on the runtime classpath, so it is used dynamically
    private static temporaryCredentials() {
        def credentials = emulatorCredentials()
        def basic = Class.forName('software.amazon.awssdk.auth.credentials.AwsBasicCredentials').create(credentials['aws_access_key_id'], credentials['aws_secret_access_key'])
        def sts = Class.forName('software.amazon.awssdk.services.sts.StsClient').builder()
            .region(Region.US_EAST_1)
            .endpointOverride(ENDPOINT)
            .credentialsProvider(Class.forName('software.amazon.awssdk.auth.credentials.StaticCredentialsProvider').create(basic))
            .build()
        try {
            sts.getSessionToken().credentials()
        } finally {
            sts.close()
        }
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
