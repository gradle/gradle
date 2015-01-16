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
import com.amazonaws.ClientConfiguration;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.S3ClientOptions;
import com.amazonaws.services.s3.model.*;
import com.google.common.base.Optional;
import org.gradle.api.artifacts.repositories.AwsCredentials;
import org.gradle.internal.resource.PasswordCredentials;
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
    private final AmazonS3Client amazonS3Client;
    private final S3ConnectionProperties s3ConnectionProperties;

    public S3Client(AmazonS3Client amazonS3Client, S3ConnectionProperties s3ConnectionProperties) {
        this.s3ConnectionProperties = s3ConnectionProperties;
        this.amazonS3Client = amazonS3Client;
    }

    public S3Client(AwsCredentials awsCredentials, S3ConnectionProperties s3ConnectionProperties) {
        S3CredentialsProvider s3CredentialsProvider = new S3CredentialsProvider(awsCredentials.getAccessKey(), awsCredentials.getSecretKey());
        this.s3ConnectionProperties = s3ConnectionProperties;
        amazonS3Client = createClient(s3CredentialsProvider);
    }

    private AmazonS3Client createClient(S3CredentialsProvider s3CredentialsProvider) {
        AmazonS3Client client = new AmazonS3Client(s3CredentialsProvider.getChain(), getClientConfiguration(s3ConnectionProperties));
        Optional<URI> endpoint = s3ConnectionProperties.getEndpoint();
        if (endpoint.isPresent()) {
            client.setEndpoint(endpoint.get().toString());
            client.setS3ClientOptions(new S3ClientOptions().withPathStyleAccess(true));
        }
        return client;
    }

    private ClientConfiguration getClientConfiguration(S3ConnectionProperties s3ConnectionProperties) {
        ClientConfiguration clientConfiguration = new ClientConfiguration();
        if (s3ConnectionProperties.getProxy().isPresent()) {
            HttpProxySettings.HttpProxy proxy = s3ConnectionProperties.getProxy().get();
            clientConfiguration.setProxyHost(proxy.host);
            clientConfiguration.setProxyPort(proxy.port);
            PasswordCredentials credentials = proxy.credentials;
            if (credentials != null) {
                clientConfiguration.setProxyUsername(credentials.getUsername());
                clientConfiguration.setProxyPassword(credentials.getPassword());
            }
        }
        return clientConfiguration;
    }

    public void put(InputStream inputStream, Long contentLength, URI destination) {
        ObjectMetadata objectMetadata = new ObjectMetadata();
        objectMetadata.setContentLength(contentLength);
        String bucketName = getBucketName(destination);
        String s3Key = getS3BucketKey(destination);
        LOGGER.debug("Attempting to put resource:[{}] into s3 bucket [{}]", s3Key, bucketName);
        try {
            amazonS3Client.putObject(bucketName, s3Key, inputStream, objectMetadata);
        } catch (AmazonClientException e) {
            throw new S3Exception(String.format("Could not put s3 resource: [%s]. %s", destination.toString(), e.getMessage()), e);
        }
    }

    public ObjectMetadata getMetaData(URI uri) {
        LOGGER.debug("Attempting to get s3 meta-data: [{}]", uri.toString());
        String bucketName = getBucketName(uri);
        String s3Key = getS3BucketKey(uri);
        try {
            return amazonS3Client.getObjectMetadata(bucketName, s3Key);
        } catch (AmazonClientException e) {
            throw new S3Exception(String.format("Could not get s3 meta-data: [%s]. %s", uri.toString(), e.getMessage()), e);
        }
    }

    public S3Object getResource(URI uri) {
        LOGGER.debug("Attempting to get s3 resource: [{}]", uri.toString());
        try {
            return amazonS3Client.getObject(getBucketName(uri), getS3BucketKey(uri));
        } catch (AmazonClientException e) {
            throw new S3Exception(String.format("Could not get s3 resource: [%s]. %s", uri.toString(), e.getMessage()), e);
        }
    }

    private String getS3BucketKey(URI destination) {
        String path = destination.getPath();
        return path.startsWith("/") ? path.substring(1) : path;
    }

    private String getBucketName(URI uri) {
        return uri.getHost();
    }

    public List<String> list(URI parent) {
        List<String> results = new ArrayList<String>();
        String bucketName = getBucketName(parent);
        String s3Key = getS3BucketKey(parent);
        ListObjectsRequest listObjectsRequest = new ListObjectsRequest()
                .withBucketName(bucketName)
                .withPrefix(s3Key)
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
}
