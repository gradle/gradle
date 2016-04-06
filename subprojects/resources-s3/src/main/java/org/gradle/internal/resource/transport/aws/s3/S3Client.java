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

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.S3ClientOptions;
import com.amazonaws.services.s3.model.*;
import com.google.common.base.Optional;
import org.gradle.api.artifacts.repositories.PasswordCredentials;
import org.gradle.api.credentials.AwsCredentials;
import org.gradle.internal.resource.ResourceExceptions;
import org.gradle.internal.resource.transport.http.HttpProxySettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class S3Client {
    private static final Logger LOGGER = LoggerFactory.getLogger(S3Client.class);
    private static final Pattern FILENAME_PATTERN = Pattern.compile("[^\\/]+\\.*$");

    private AmazonS3Client amazonS3Client;
    private final S3ConnectionProperties s3ConnectionProperties;

    public S3Client(AmazonS3Client amazonS3Client, S3ConnectionProperties s3ConnectionProperties) {
        this.s3ConnectionProperties = s3ConnectionProperties;
        this.amazonS3Client = amazonS3Client;
    }

    public S3Client(AwsCredentials awsCredentials, S3ConnectionProperties s3ConnectionProperties) {
        this.s3ConnectionProperties = s3ConnectionProperties;
        AWSCredentials credentials = awsCredentials == null ? null : new BasicAWSCredentials(awsCredentials.getAccessKey(), awsCredentials.getSecretKey());
        amazonS3Client = createAmazonS3Client(credentials);
    }

    private AmazonS3Client createAmazonS3Client(AWSCredentials credentials) {
        AmazonS3Client amazonS3Client = new AmazonS3Client(credentials, createConnectionProperties());
        S3ClientOptions clientOptions = new S3ClientOptions();
        Optional<URI> endpoint = s3ConnectionProperties.getEndpoint();
        if (endpoint.isPresent()) {
            amazonS3Client.setEndpoint(endpoint.get().toString());
            clientOptions.withPathStyleAccess(true);
        }
        amazonS3Client.setS3ClientOptions(clientOptions);
        return amazonS3Client;
    }

    private ClientConfiguration createConnectionProperties() {
        ClientConfiguration clientConfiguration = new ClientConfiguration();
        Optional<HttpProxySettings.HttpProxy> proxyOptional = s3ConnectionProperties.getProxy();
        if (proxyOptional.isPresent()) {
            HttpProxySettings.HttpProxy proxy = s3ConnectionProperties.getProxy().get();
            clientConfiguration.setProxyHost(proxy.host);
            clientConfiguration.setProxyPort(proxy.port);
            PasswordCredentials credentials = proxy.credentials;
            if (credentials != null) {
                clientConfiguration.setProxyUsername(credentials.getUsername());
                clientConfiguration.setProxyPassword(credentials.getPassword());
            }
        }
        Optional<Integer> maxErrorRetryCount = s3ConnectionProperties.getMaxErrorRetryCount();
        if (maxErrorRetryCount.isPresent()) {
            clientConfiguration.setMaxErrorRetry(maxErrorRetryCount.get());
        }
        return clientConfiguration;
    }

    public void put(InputStream inputStream, Long contentLength, URI destination) {
        try {
            S3RegionalResource s3RegionalResource = new S3RegionalResource(destination);
            String bucketName = s3RegionalResource.getBucketName();
            String s3BucketKey = s3RegionalResource.getKey();
            configureClient(s3RegionalResource);

            ObjectMetadata objectMetadata = new ObjectMetadata();
            objectMetadata.setContentLength(contentLength);

            PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, s3BucketKey, inputStream, objectMetadata);
            LOGGER.debug("Attempting to put resource:[{}] into s3 bucket [{}]", s3BucketKey, bucketName);

            amazonS3Client.putObject(putObjectRequest);
        } catch (AmazonClientException e) {
            throw ResourceExceptions.putFailed(destination, e);
        }
    }

    public S3Object getMetaData(URI uri) {
        LOGGER.debug("Attempting to get s3 meta-data: [{}]", uri.toString());
        //Would typically use GetObjectMetadataRequest but it does not work with v4 signatures
        return doGetS3Object(uri, true);
    }

    public S3Object getResource(URI uri) {
        LOGGER.debug("Attempting to get s3 resource: [{}]", uri.toString());
        return doGetS3Object(uri, false);
    }

    public List<String> list(URI parent) {
        List<String> results = new ArrayList<String>();

        S3RegionalResource s3RegionalResource = new S3RegionalResource(parent);
        String bucketName = s3RegionalResource.getBucketName();
        String s3BucketKey = s3RegionalResource.getKey();
        configureClient(s3RegionalResource);

        ListObjectsRequest listObjectsRequest = new ListObjectsRequest()
                .withBucketName(bucketName)
                .withPrefix(s3BucketKey)
                .withMaxKeys(1000)
                .withDelimiter("/");
        ObjectListing objectListing = amazonS3Client.listObjects(listObjectsRequest);
        results.addAll(resolveResourceNames(objectListing));

        while (objectListing.isTruncated()) {
            objectListing = amazonS3Client.listNextBatchOfObjects(objectListing);
            results.addAll(resolveResourceNames(objectListing));
        }
        return results;
    }

    private List<String> resolveResourceNames(ObjectListing objectListing) {
        List<String> results = new ArrayList<String>();
        List<S3ObjectSummary> objectSummaries = objectListing.getObjectSummaries();
        if (null != objectSummaries) {
            for (S3ObjectSummary objectSummary : objectSummaries) {
                String key = objectSummary.getKey();
                String fileName = extractResourceName(key);
                if (null != fileName) {
                    results.add(fileName);
                }
            }
        }
        return results;
    }

    private String extractResourceName(String key) {
        Matcher matcher = FILENAME_PATTERN.matcher(key);
        if (matcher.find()) {
            String group = matcher.group(0);
            return group.contains(".") ? group : null;
        }
        return null;
    }

    private S3Object doGetS3Object(URI uri, boolean isLightWeight) {
        S3RegionalResource s3RegionalResource = new S3RegionalResource(uri);
        String bucketName = s3RegionalResource.getBucketName();
        String s3BucketKey = s3RegionalResource.getKey();
        configureClient(s3RegionalResource);

        GetObjectRequest getObjectRequest = new GetObjectRequest(bucketName, s3BucketKey);
        if (isLightWeight) {
            //Skip content download
            getObjectRequest.setRange(0, 0);
        }

        try {
            return amazonS3Client.getObject(getObjectRequest);
        } catch (AmazonServiceException e) {
            String errorCode = e.getErrorCode();
            if (null != errorCode && errorCode.equalsIgnoreCase("NoSuchKey")) {
                return null;
            }
            throw ResourceExceptions.getFailed(uri, e);
        }
    }

    private void configureClient(S3RegionalResource s3RegionalResource) {
        if (s3ConnectionProperties.getEndpoint().isPresent()) {
            amazonS3Client.setEndpoint(s3ConnectionProperties.getEndpoint().get().toString());
        } else {
            amazonS3Client.setRegion(s3RegionalResource.getRegion());
        }
    }
}
