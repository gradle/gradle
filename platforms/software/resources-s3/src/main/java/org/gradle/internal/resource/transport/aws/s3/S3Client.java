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

package org.gradle.internal.resource.transport.aws.s3;

import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import org.gradle.api.credentials.AwsCredentials;
import org.gradle.internal.resource.ReadableContent;
import org.gradle.internal.resource.ResourceExceptions;
import org.gradle.internal.resource.transport.http.HttpProxySettings;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProviderChain;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.retries.DefaultRetryStrategy;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.ContentStreamProvider;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.apache5.Apache5HttpClient;
import software.amazon.awssdk.http.apache5.ProxyConfiguration;
import software.amazon.awssdk.profiles.ProfileFile;
import software.amazon.awssdk.profiles.ProfileFileSystemSetting;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class S3Client {
    private static final Logger LOGGER = LoggerFactory.getLogger(S3Client.class);
    // AWS SDK v2 requires a region at builder time; v1 silently defaulted to us-east-1.
    // Preserve that behavior when no region has been derived from a bucket URL.
    private static final Region DEFAULT_REGION = Region.US_EAST_1;
    private static final String OCTET_STREAM = "application/octet-stream";
    // SDK v1's defaults; SDK v2 defaults to 2 seconds and 30 seconds.
    private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration SOCKET_TIMEOUT = Duration.ofSeconds(50);
    // The region in a custom endpoint's host, as SDK v1 derived it, such as s3.eu-west-1.amazonaws.com or s3.eu-central-1.wasabisys.com
    private static final Pattern AWS_ENDPOINT_HOST = Pattern.compile("^(?:.+\\.)?s3(?:-fips)?(?:[.-]dualstack)?[.-]([a-z0-9-]+)\\.amazonaws\\.com(?:\\.cn)?$");
    private static final Pattern OTHER_ENDPOINT_HOST = Pattern.compile("^(?:.+\\.)?s3\\.([a-z0-9-]+)\\.");

    private final software.amazon.awssdk.services.s3.@Nullable S3Client amazonS3ClientMock;
    private AwsCredentialsProvider credentialsProvider = AnonymousCredentialsProvider.create();
    private final Map<Region, software.amazon.awssdk.services.s3.S3Client> clientsByRegion = new ConcurrentHashMap<>();
    private final S3ResourceResolver resourceResolver = new S3ResourceResolver();
    private final S3ConnectionProperties s3ConnectionProperties;

    public S3Client(software.amazon.awssdk.services.s3.@Nullable S3Client amazonS3ClientMock,
                    S3ConnectionProperties s3ConnectionProperties) {
        this.amazonS3ClientMock = amazonS3ClientMock;
        this.s3ConnectionProperties = s3ConnectionProperties;
    }

    /**
     * Constructor without provided credentials to delegate to the default provider chain.
     * @since 3.1
     */
    public S3Client(S3ConnectionProperties s3ConnectionProperties) {
        this((software.amazon.awssdk.services.s3.S3Client) null, s3ConnectionProperties);
        // SDK v1's S3 chain fell back to anonymous access when no credentials were found; the v2 chain throws.
        // Each request walks the chain, so that a transient lookup failure doesn't stick to the anonymous provider.
        this.credentialsProvider = AwsCredentialsProviderChain.builder()
            .reuseLastProviderEnabled(false)
            .credentialsProviders(DefaultCredentialsProvider.builder().build(), AnonymousCredentialsProvider.create())
            .build();
    }

    public S3Client(@Nullable AwsCredentials awsCredentials, S3ConnectionProperties s3ConnectionProperties) {
        this.amazonS3ClientMock = null;
        this.s3ConnectionProperties = s3ConnectionProperties;
        if (awsCredentials != null) {
            if (awsCredentials.getSessionToken() == null) {
                this.credentialsProvider = StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(awsCredentials.getAccessKey(), awsCredentials.getSecretKey())
                );
            } else {
                this.credentialsProvider = StaticCredentialsProvider.create(
                    AwsSessionCredentials.create(
                        awsCredentials.getAccessKey(), awsCredentials.getSecretKey(), awsCredentials.getSessionToken()
                    )
                );
            }
        }
    }

    private software.amazon.awssdk.services.s3.S3Client clientFor(S3RegionalResource s3RegionalResource) {
        if (amazonS3ClientMock != null) {
            return amazonS3ClientMock;
        }
        Optional<URI> endpoint = s3ConnectionProperties.getEndpoint();
        Region region = endpoint.isPresent()
            ? signingRegion(endpoint.get())
            : s3RegionalResource.getRegion().orElse(DEFAULT_REGION);
        return clientsByRegion.computeIfAbsent(region, this::build);
    }

    private software.amazon.awssdk.services.s3.S3Client build(Region region) {
        // We can't change the region of a built software.amazon.awssdk.services.s3.S3Client,
        // and we don't know the region until we know the bucket we will be accessing.
        S3ClientBuilder clientBuilder = software.amazon.awssdk.services.s3.S3Client.builder()
            .httpClientBuilder(createProxyConfiguration())
            .overrideConfiguration(createConnectionProperties())
            .credentialsProvider(credentialsProvider)
            .region(region);
        applyS3ConnectionProperties(clientBuilder, s3ConnectionProperties);
        return clientBuilder.build();
    }

    private void applyS3ConnectionProperties(
        S3ClientBuilder clientBuilder, S3ConnectionProperties s3ConnectionProperties) {

        Optional<URI> endpointProperty = s3ConnectionProperties.getEndpoint();
        if (endpointProperty.isPresent()) {
            clientBuilder.endpointOverride(endpointProperty.get());
            S3Configuration.Builder s3configurationBuilder = S3Configuration.builder();
            s3configurationBuilder
                .pathStyleAccessEnabled(true)
                .chunkedEncodingEnabled(false);
            clientBuilder.serviceConfiguration(s3configurationBuilder.build());
        } else {
            // Region-less URLs start at the default region, so the SDK has to follow S3's redirect to the bucket's region.
            clientBuilder.crossRegionAccessEnabled(true);
        }
    }

    static Region signingRegion(URI endpoint) {
        String host = endpoint.getHost();
        if (host == null) {
            return DEFAULT_REGION;
        }
        host = host.toLowerCase(Locale.ROOT);
        Matcher aws = AWS_ENDPOINT_HOST.matcher(host);
        if (aws.matches()) {
            return aws.group(1).equals("external-1") ? Region.US_EAST_1 : Region.of(aws.group(1));
        }
        if (host.endsWith(".amazonaws.com") || host.endsWith(".amazonaws.com.cn")) {
            return DEFAULT_REGION;
        }
        Matcher other = OTHER_ENDPOINT_HOST.matcher(host);
        return other.find() ? Region.of(other.group(1)) : DEFAULT_REGION;
    }

    private ClientOverrideConfiguration createConnectionProperties() {
        ClientOverrideConfiguration.Builder clientOverrideConfigurationBuilder = ClientOverrideConfiguration.builder();
        ignoreConfiguredEndpointUrls(clientOverrideConfigurationBuilder);
        Optional<Integer> maxErrorRetryCount = s3ConnectionProperties.getMaxErrorRetryCount();
        if (maxErrorRetryCount.isPresent()) {
            // maxAttempts includes the initial attempt; numRetries counted only retries. Preserve behavior with +1.
            clientOverrideConfigurationBuilder.retryStrategy(
                DefaultRetryStrategy.legacyStrategyBuilder()
                    .maxAttempts(maxErrorRetryCount.get() + 1)
                    .build()
            );
        }
        return clientOverrideConfigurationBuilder.build();
    }

    /**
     * SDK v2 also takes endpoints from AWS_ENDPOINT_URL, aws.endpointUrl and the profile's endpoint_url, which SDK v1 ignored.
     * Gradle only uses org.gradle.s3.endpoint, so turn that off for the active profile and keep its other settings.
     */
    private static void ignoreConfiguredEndpointUrls(ClientOverrideConfiguration.Builder builder) {
        String profileName = ProfileFileSystemSetting.AWS_PROFILE.getStringValue().orElse("default");
        String section = profileName.equals("default") ? "[default]" : "[profile " + profileName + "]";
        ProfileFile ignoreEndpointUrls = ProfileFile.builder()
            .content(section + "\nignore_configured_endpoint_urls = true\n")
            .type(ProfileFile.Type.CONFIGURATION)
            .build();
        builder.defaultProfileName(profileName);
        builder.defaultProfileFileSupplier(() -> ProfileFile.aggregator()
            .addFile(ProfileFile.defaultProfileFile())
            .addFile(ignoreEndpointUrls)
            .build());
    }

    private SdkHttpClient.Builder<Apache5HttpClient.Builder> createProxyConfiguration() {
        ProxyConfiguration.Builder proxyConfigurationBuilder = ProxyConfiguration.builder();
        Optional<HttpProxySettings.HttpProxy> proxyOptional = s3ConnectionProperties.getProxy();
        if (proxyOptional.isPresent()) {
            HttpProxySettings.HttpProxy proxy = proxyOptional.get();
            URI uri = URI.create(String.format("http://%s:%s", proxy.host, proxy.port));
            proxyConfigurationBuilder.endpoint(uri);
            HttpProxySettings.HttpProxyCredentials credentials = proxy.credentials;
            if (credentials != null) {
                proxyConfigurationBuilder
                    .username(credentials.getUsername())
                    .password(credentials.getPassword());
            }
        }
        return Apache5HttpClient.builder()
            .connectionTimeout(CONNECTION_TIMEOUT)
            .socketTimeout(SOCKET_TIMEOUT)
            .proxyConfiguration(proxyConfigurationBuilder.build());
    }

    public void put(ReadableContent content, URI destination) {
        if (content.getContentLength() < s3ConnectionProperties.getMultipartThreshold()) {
            putSingleObject(content, destination);
        } else {
            putMultiPartObject(content, destination);
        }
    }

    private void putSingleObject(ReadableContent content, URI destination) {
        long contentLength = content.getContentLength();
        try (ContentRangeProvider body = new ContentRangeProvider(content, 0, contentLength)) {
            S3RegionalResource s3RegionalResource = new S3RegionalResource(destination);
            String bucketName = s3RegionalResource.getBucketName();
            String s3BucketKey = s3RegionalResource.getKey();

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(s3BucketKey)
                .acl(ObjectCannedACL.BUCKET_OWNER_FULL_CONTROL)
                .contentLength(contentLength).build();
            LOGGER.debug("Attempting to put resource:[{}] into s3 bucket [{}]", s3BucketKey, bucketName);

            clientFor(s3RegionalResource).putObject(putObjectRequest, RequestBody.fromContentProvider(body, contentLength, OCTET_STREAM));
        } catch (SdkException | IOException e) {
            throw ResourceExceptions.putFailed(destination, e);
        }
    }

    private void putMultiPartObject(ReadableContent content, URI destination) {
        try {
            long contentLength = content.getContentLength();
            S3RegionalResource s3RegionalResource = new S3RegionalResource(destination);
            String bucketName = s3RegionalResource.getBucketName();
            String s3BucketKey = s3RegionalResource.getKey();
            List<CompletedPart> partETags = new ArrayList<>();
            CreateMultipartUploadRequest initRequest = CreateMultipartUploadRequest.builder()
                .bucket(bucketName)
                .key(s3BucketKey)
                .acl(ObjectCannedACL.BUCKET_OWNER_FULL_CONTROL)
                .build();

            software.amazon.awssdk.services.s3.S3Client amazonS3Client = clientFor(s3RegionalResource);
            CreateMultipartUploadResponse initResponse = amazonS3Client.createMultipartUpload(initRequest);
            try {
                long filePosition = 0;
                long partSize = s3ConnectionProperties.getPartSize();

                LOGGER.debug("Attempting to put multi-part resource:[{}] into s3 bucket [{}]", s3BucketKey, bucketName);

                for (int partNumber = 1; filePosition < contentLength; partNumber++) {
                    partSize = Math.min(partSize, contentLength - filePosition);
                    UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                        .bucket(bucketName)
                        .key(s3BucketKey)
                        .uploadId(initResponse.uploadId())
                        .partNumber(partNumber)
                        .build();
                    String eTag;
                    try (ContentRangeProvider part = new ContentRangeProvider(content, filePosition, partSize)) {
                        eTag = amazonS3Client.uploadPart(uploadPartRequest, RequestBody.fromContentProvider(part, partSize, OCTET_STREAM)).eTag();
                    }
                    CompletedPart completedPart = CompletedPart.builder().partNumber(partNumber).eTag(eTag).build();
                    partETags.add(completedPart);
                    filePosition += partSize;
                }

                CompletedMultipartUpload completedMultipartUpload = CompletedMultipartUpload.builder()
                    .parts(partETags)
                    .build();

                CompleteMultipartUploadRequest completeRequest = CompleteMultipartUploadRequest.builder()
                    .bucket(bucketName)
                    .key(s3BucketKey)
                    .uploadId(initResponse.uploadId())
                    .multipartUpload(completedMultipartUpload)
                    .build();
                amazonS3Client.completeMultipartUpload(completeRequest);
            } catch (RuntimeException | IOException e) {
                AbortMultipartUploadRequest abortRequest = AbortMultipartUploadRequest.builder()
                    .bucket(bucketName)
                    .key(s3BucketKey)
                    .uploadId(initResponse.uploadId())
                    .build();
                amazonS3Client.abortMultipartUpload(abortRequest);
                throw e;
            }
        } catch (SdkException | IOException e) {
            throw ResourceExceptions.putFailed(destination, e);
        }
    }

    @Nullable
    public HeadObjectResponse getMetaData(URI uri) {
        S3RegionalResource s3RegionalResource = new S3RegionalResource(uri);
        String bucketName = s3RegionalResource.getBucketName();
        String s3BucketKey = s3RegionalResource.getKey();

        software.amazon.awssdk.services.s3.S3Client amazonS3Client = clientFor(s3RegionalResource);
        try {
            return amazonS3Client.headObject(objectRequest -> objectRequest.bucket(bucketName).key(s3BucketKey));
        } catch (S3Exception e) {
            if (e.statusCode() != 404) {
                throw ResourceExceptions.getFailed(uri, e.statusCode(), e);
            }
            confirmMissingKey(amazonS3Client, bucketName, s3BucketKey, uri);
            return null;
        }
    }

    /**
     * A HEAD 404 has no body, and the SDK reports every one as NoSuchKey, so a missing bucket looks like a missing key.
     * A GET for the same key gets S3's real error code.
     */
    private static void confirmMissingKey(software.amazon.awssdk.services.s3.S3Client amazonS3Client, String bucketName, String s3BucketKey, URI uri) {
        try {
            // An object created since the HEAD is still reported as missing for this request
            amazonS3Client.getObject(objectRequest -> objectRequest.bucket(bucketName).key(s3BucketKey)).abort();
        } catch (S3Exception e) {
            if (!isNoSuchKey(e)) {
                throw ResourceExceptions.getFailed(uri, e.statusCode(), e);
            }
        }
    }

    @Nullable
    public ResponseInputStream<GetObjectResponse> getResource(URI uri) {
        S3RegionalResource s3RegionalResource = new S3RegionalResource(uri);
        String bucketName = s3RegionalResource.getBucketName();
        String s3BucketKey = s3RegionalResource.getKey();

        try {
            return clientFor(s3RegionalResource).getObject(objectRequest -> objectRequest.bucket(bucketName).key(s3BucketKey));
        } catch (S3Exception e) {
            if (isNoSuchKey(e)) {
                return null;
            }
            throw ResourceExceptions.getFailed(uri, e.statusCode(), e);
        }
    }

    public List<String> listDirectChildren(URI parent) {
        S3RegionalResource s3RegionalResource = new S3RegionalResource(parent);
        String bucketName = s3RegionalResource.getBucketName();
        String s3BucketKey = s3RegionalResource.getKey();

        ListObjectsV2Request listObjectsV2Request = ListObjectsV2Request.builder()
            .bucket(bucketName)
            .prefix(s3BucketKey)
            .maxKeys(1000)
            .delimiter("/")
            .build();
        ImmutableList.Builder<String> listBuilder = ImmutableList.builder();
        try {
            ListObjectsV2Iterable objectListing = clientFor(s3RegionalResource).listObjectsV2Paginator(listObjectsV2Request);
            objectListing.forEach(listObjectsResponse ->
                listBuilder.addAll(resourceResolver.resolveResourceNames(listObjectsResponse))
            );
        } catch (S3Exception e) {
            throw ResourceExceptions.getFailed(parent, e.statusCode(), e);
        }
        return listBuilder.build();
    }

    // A GET 404 normally carries an error code. Treat a 404 without one, such as from an
    // S3-compatible server that sends no body, as a missing key, so that unrelated 404s
    // (NoSuchBucket, endpoint proxies, etc.) still fail instead of looking like a missing resource.
    private static boolean isNoSuchKey(S3Exception e) {
        AwsErrorDetails details = e.awsErrorDetails();
        String errorCode = details == null ? null : details.errorCode();
        return "NoSuchKey".equalsIgnoreCase(errorCode)
            || (errorCode == null && e.statusCode() == 404);
    }

    /**
     * Supplies a byte range of the content, reopening it each time the SDK re-reads the body
     * for signing or a retry, so that uploads stay off the heap.
     */
    private static final class ContentRangeProvider implements ContentStreamProvider, Closeable {
        private final ReadableContent content;
        private final long offset;
        private final long length;
        private @Nullable InputStream current;

        ContentRangeProvider(ReadableContent content, long offset, long length) {
            this.content = content;
            this.offset = offset;
            this.length = length;
        }

        @Override
        public InputStream newStream() {
            try {
                close();
                InputStream stream = content.open();
                current = stream;
                ByteStreams.skipFully(stream, offset);
                return ByteStreams.limit(stream, length);
            } catch (IOException e) {
                throw SdkClientException.create("Could not read the content to upload.", e);
            }
        }

        @Override
        public void close() throws IOException {
            if (current != null) {
                current.close();
                current = null;
            }
        }
    }
}
