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

import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.apache.ProxyConfiguration;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;
import software.amazon.awssdk.core.sync.RequestBody;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import org.gradle.api.credentials.PasswordCredentials;
import org.gradle.api.credentials.AwsCredentials;
import org.gradle.internal.resource.ResourceExceptions;
import org.gradle.internal.resource.transport.http.HttpProxySettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("deprecation")
public class S3Client {
    private static final Logger LOGGER = LoggerFactory.getLogger(S3Client.class);
    private static final Map<String, Region> KNOWN_BUCKET_REGIONS = new HashMap<>();

    // amazonS3ClientMock is used to configure Mocks for testing.
    private software.amazon.awssdk.services.s3.S3Client amazonS3ClientMock = null;
    private StaticCredentialsProvider credentialsProvider = null;
    private URI endpoint = null;
    private Region region = null;
    private final S3ResourceResolver resourceResolver = new S3ResourceResolver();
    private S3ConnectionProperties s3ConnectionProperties = null;

    /**
     * Constructor without provided credentials to delegate to the default provider chain.
     * @since 3.1
     */
    public S3Client(software.amazon.awssdk.services.s3.S3Client amazonS3ClientMock,
                    S3ConnectionProperties s3ConnectionProperties) {
        this.amazonS3ClientMock = amazonS3ClientMock;
        this.s3ConnectionProperties = s3ConnectionProperties;
    }
    public S3Client(S3ConnectionProperties s3ConnectionProperties) {
        this.s3ConnectionProperties = s3ConnectionProperties;
    }

    public S3Client(AwsCredentials awsCredentials, S3ConnectionProperties s3ConnectionProperties) {
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
        } else {
            this.credentialsProvider = null;
        }
    }

    public static final class GetResourceResponse {
        private final software.amazon.awssdk.services.s3.S3Client amazonS3Client;
        private final ResponseInputStream<GetObjectResponse> responseInputStream;

        GetResourceResponse(software.amazon.awssdk.services.s3.S3Client amazonS3Client,
                            ResponseInputStream<GetObjectResponse> responseInputStream) {
            this.amazonS3Client = amazonS3Client;
            this.responseInputStream = responseInputStream;
        }

        public void close() throws IOException{
            getResponseInputStream().close();
            amazonS3Client.close();
        }

        public ResponseInputStream<GetObjectResponse> getResponseInputStream() {
            return responseInputStream;
        }
    }

    private software.amazon.awssdk.services.s3.S3Client build() {
        if (amazonS3ClientMock != null) {
            return amazonS3ClientMock;
        }

        // We can't change the region of a built software.amazon.awssdk.services.s3.S3Client.
        // We don't know the region until we know the bucket we will be accessing.
        // Defer building the client until we receive an access request.
        // Reusing builders can put them into a bad state.
        // Create a new builder from saved properties when needed.
        S3ClientBuilder clientBuilder = software.amazon.awssdk.services.s3.S3Client.builder()
            .httpClientBuilder(createProxyConfiguration())
            .overrideConfiguration(createConnectionProperties());

        if (credentialsProvider != null) {
            clientBuilder.credentialsProvider(credentialsProvider);
        }
        if (endpoint != null) {
            clientBuilder.endpointOverride(endpoint);
        }
        if (region != null) {
            clientBuilder.region(region);
        }
        if (s3ConnectionProperties != null) {
            clientBuilder.applyMutation(builder -> applyS3ConnectionProperties(builder, s3ConnectionProperties));

        }
        return clientBuilder.build();
    }

    private static void applyS3ConnectionProperties(
        S3ClientBuilder clientBuilder, S3ConnectionProperties s3ConnectionProperties) {

        Optional<URI> endpointProperty = s3ConnectionProperties.getEndpoint();
        if (endpointProperty.isPresent()) {
            clientBuilder.endpointOverride(endpointProperty.get());
            S3Configuration.Builder s3configurationBuilder = S3Configuration.builder();
            s3configurationBuilder
                .pathStyleAccessEnabled(true)
                .chunkedEncodingEnabled(false);
            clientBuilder.serviceConfiguration(s3configurationBuilder.build());
        }
    }

    private ClientOverrideConfiguration createConnectionProperties() {
        ClientOverrideConfiguration.Builder clientOverrideConfigurationBuilder = ClientOverrideConfiguration.builder();
        Optional<Integer> maxErrorRetryCount = s3ConnectionProperties.getMaxErrorRetryCount();
        if (maxErrorRetryCount.isPresent()) {
            RetryPolicy retryPolicy = RetryPolicy.builder()
                .numRetries(maxErrorRetryCount.get())
                .build();
            clientOverrideConfigurationBuilder.retryPolicy(retryPolicy);
        }
        return clientOverrideConfigurationBuilder.build();
    }

    private  SdkHttpClient.Builder<ApacheHttpClient.Builder> createProxyConfiguration() {
        ProxyConfiguration.Builder proxyConfigurationBuilder = ProxyConfiguration.builder();
        Optional<HttpProxySettings.HttpProxy> proxyOptional = s3ConnectionProperties.getProxy();
        if (proxyOptional.isPresent()) {
            HttpProxySettings.HttpProxy proxy = proxyOptional.get();
            URI uri = URI.create(String.format("http://%s:%s", proxy.host, proxy.port));
            proxyConfigurationBuilder.endpoint(uri);
            PasswordCredentials credentials = proxy.credentials;
            if (credentials != null) {
                proxyConfigurationBuilder
                    .username(credentials.getUsername())
                    .password(credentials.getPassword());
            }
        }
        return ApacheHttpClient.builder()
            .proxyConfiguration(proxyConfigurationBuilder.build());
    }

    public void put(InputStream inputStream, Long contentLength, URI destination) {
        if (contentLength < s3ConnectionProperties.getMultipartThreshold()) {
            putSingleObject(inputStream, contentLength, destination);
        } else {
            putMultiPartObject(inputStream, contentLength, destination);
        }
    }

    private void putSingleObject(InputStream inputStream, Long contentLength, URI destination) {
        try {
            S3RegionalResource s3RegionalResource = new S3RegionalResource(destination, KNOWN_BUCKET_REGIONS);
            String bucketName = s3RegionalResource.getBucketName();
            String s3BucketKey = s3RegionalResource.getKey();
            configureClient(s3RegionalResource);

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(s3BucketKey)
                .acl(ObjectCannedACL.BUCKET_OWNER_FULL_CONTROL)
                .contentLength(contentLength).build();
            LOGGER.debug("Attempting to put resource:[{}] into s3 bucket [{}]", s3BucketKey, bucketName);

            try (software.amazon.awssdk.services.s3.S3Client amazonS3Client = build()) {
                amazonS3Client.putObject(putObjectRequest, RequestBody.fromInputStream(inputStream, contentLength));
            }
        } catch (SdkException e) {
            throw ResourceExceptions.putFailed(destination, e);
        }
    }

    private void putMultiPartObject(InputStream inputStream, Long contentLength, URI destination) {
        try {
            S3RegionalResource s3RegionalResource = new S3RegionalResource(destination, KNOWN_BUCKET_REGIONS);
            String bucketName = s3RegionalResource.getBucketName();
            String s3BucketKey = s3RegionalResource.getKey();
            configureClient(s3RegionalResource);
            List<CompletedPart> partETags = new ArrayList<>();
            CreateMultipartUploadRequest initRequest = CreateMultipartUploadRequest.builder()
                .bucket(bucketName)
                .key(s3BucketKey)
                .acl(ObjectCannedACL.BUCKET_OWNER_FULL_CONTROL)
                .build();

            try (software.amazon.awssdk.services.s3.S3Client amazonS3Client = build()) {
                CreateMultipartUploadResponse initResponse = amazonS3Client.createMultipartUpload(initRequest);
                try {
                    long filePosition = 0;
                    long partSize = s3ConnectionProperties.getPartSize();

                    LOGGER.debug("Attempting to put resource:[{}] into s3 bucket [{}]", s3BucketKey, bucketName);

                    for (int partNumber = 1; filePosition < contentLength; partNumber++) {
                        partSize = Math.min(partSize, contentLength - filePosition);
                        RequestBody requestBody = RequestBody.fromInputStream(inputStream, partSize);
                        UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                            .bucket(bucketName)
                            .key(s3BucketKey)
                            .uploadId(initResponse.uploadId())
                            .partNumber(partNumber)
                            .build();
                        String eTag = amazonS3Client.uploadPart(uploadPartRequest, requestBody).eTag();
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
                } catch (SdkException e) {
                    AbortMultipartUploadRequest abortRequest = AbortMultipartUploadRequest.builder()
                        .bucket(bucketName)
                        .key(s3BucketKey)
                        .uploadId(initResponse.uploadId())
                        .build();
                    amazonS3Client.abortMultipartUpload(abortRequest);
                    throw e;
                }
            }
        } catch (SdkException e) {
            throw ResourceExceptions.putFailed(destination, e);
        }
    }

    public HeadObjectResponse getMetaData(URI uri) {
        S3RegionalResource s3RegionalResource = new S3RegionalResource(uri, KNOWN_BUCKET_REGIONS);
        String bucketName = s3RegionalResource.getBucketName();
        String s3BucketKey = s3RegionalResource.getKey();
        configureClient(s3RegionalResource);

        try (software.amazon.awssdk.services.s3.S3Client amazonS3Client = build()) {
            return amazonS3Client.headObject(
                objectRequest -> objectRequest.bucket(bucketName).key(s3BucketKey));
        } catch (S3Exception e) {
            if (exceptionIsRedirect(e)) {
                Optional<String> region = regionFromRedirectException(e);
                if (region.isPresent()) {
                    S3Client.KNOWN_BUCKET_REGIONS.put(bucketName, Region.of(region.get()));
                }
                Optional<URI> location = locationFromRedirectException(e);
                if (location.isPresent()) {
                    return getMetaData(location.get());
                } else {
                    return getMetaData(uri);
                }
            } else {
                String errorCode = e.awsErrorDetails().errorCode();
                if (null != errorCode && errorCode.equalsIgnoreCase("NoSuchKey")) {
                    return null;
                }
                throw ResourceExceptions.getFailed(uri, e);
            }
        }
    }

    public GetResourceResponse getResource(URI uri) {
        S3RegionalResource s3RegionalResource = new S3RegionalResource(uri, KNOWN_BUCKET_REGIONS);
        String bucketName = s3RegionalResource.getBucketName();
        String s3BucketKey = s3RegionalResource.getKey();
        configureClient(s3RegionalResource);

        // The amazonS3Client is auto-closable, but we can't wrap this with a try-with-resources
        // block because the ResponseInputStream needs to be read after this method returns.
        software.amazon.awssdk.services.s3.S3Client amazonS3Client = build();

        try {
            return new GetResourceResponse(
                amazonS3Client,
                amazonS3Client.getObject(objectRequest -> objectRequest.bucket(bucketName).key(s3BucketKey)));
        } catch (S3Exception e) {
            amazonS3Client.close();
            if (exceptionIsRedirect(e)) {
                Optional<String> region = regionFromRedirectException(e);
                if (region.isPresent()) {
                    S3Client.KNOWN_BUCKET_REGIONS.put(bucketName, Region.of(region.get()));
                }
                Optional<URI> location = locationFromRedirectException(e);
                if (location.isPresent()) {
                    return getResource(location.get());
                } else {
                    return getResource(uri);
                }
            } else {
                String errorCode = e.awsErrorDetails().errorCode();
                if (null != errorCode && errorCode.equalsIgnoreCase("NoSuchKey")) {
                    return null;
                }
                throw ResourceExceptions.getFailed(uri, e);
            }
        }
    }

    public List<String> listDirectChildren(URI parent) {
        S3RegionalResource s3RegionalResource = new S3RegionalResource(parent, KNOWN_BUCKET_REGIONS);
        String bucketName = s3RegionalResource.getBucketName();
        String s3BucketKey = s3RegionalResource.getKey();
        configureClient(s3RegionalResource);

        ListObjectsV2Request listObjectsV2Request = ListObjectsV2Request.builder()
            .bucket(bucketName)
            .prefix(s3BucketKey)
            .maxKeys(1000)
            .delimiter("/")
            .build();
        ImmutableList.Builder<String> listBuilder = ImmutableList.builder();
        try (software.amazon.awssdk.services.s3.S3Client amazonS3Client = build()) {
            ListObjectsV2Iterable objectListing = amazonS3Client.listObjectsV2Paginator(listObjectsV2Request);
            objectListing.forEach(listObjectsResponse ->
                listBuilder.addAll(resourceResolver.resolveResourceNames(listObjectsResponse))
            );
        }
        return listBuilder.build();
    }

    private void configureClient(S3RegionalResource s3RegionalResource) {
        Optional<URI> endpointProperty = s3ConnectionProperties.getEndpoint();
        if (endpointProperty.isPresent()) {
            endpoint = endpointProperty.get();
        } else {
            Optional<Region> regionResource = s3RegionalResource.getRegion();
            if (regionResource.isPresent()) {
                region = regionResource.get();
            }
        }
    }

    private boolean exceptionIsRedirect(S3Exception e) {
        return e.statusCode() == 301 || e.statusCode() == 307;
    }

    private Optional<URI> locationFromRedirectException(S3Exception e) {
        java.util.Optional<String> locationHeader = e
            .awsErrorDetails()
            .sdkHttpResponse()
            .firstMatchingHeader("Location");
        // Convert from java.util.Optional to com.google.common.base.Optional.
        return locationHeader.map(h -> Optional.of(URI.create(h))).orElseGet(Optional::absent);
    }

    private Optional<String> regionFromRedirectException(S3Exception e) {
        java.util.Optional<String> bucketRegionHeader = e
            .awsErrorDetails()
            .sdkHttpResponse()
            .firstMatchingHeader("x-amz-bucket-region");
        // Convert from java.util.Optional to com.google.common.base.Optional.
        return bucketRegionHeader.map(Optional::of).orElseGet(Optional::absent);
    }
 }
