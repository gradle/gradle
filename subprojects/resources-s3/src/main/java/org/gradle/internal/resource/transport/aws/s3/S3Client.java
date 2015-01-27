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

import com.google.common.base.Optional;
import org.gradle.api.artifacts.repositories.AwsCredentials;
import org.gradle.internal.resource.PasswordCredentials;
import org.gradle.internal.resource.transport.http.HttpProxySettings;
import org.jets3t.service.Constants;
import org.jets3t.service.Jets3tProperties;
import org.jets3t.service.S3ServiceException;
import org.jets3t.service.ServiceException;
import org.jets3t.service.impl.rest.httpclient.RestS3Service;
import org.jets3t.service.model.S3Object;
import org.jets3t.service.model.StorageObject;
import org.jets3t.service.security.AWSCredentials;
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
    public static final String S3SERVICE_S3_ENDPOINT = "s3service.s3-endpoint";
    public static final String S3SERVICE_S3_ENDPOINT_HTTP_PORT = "s3service.s3-endpoint-http-port";
    public static final String S3SERVICE_HTTPS_ONLY = "s3service.https-only";
    public static final String S3SERVICE_DISABLE_DNS_BUCKETS = "s3service.disable-dns-buckets";
    public static final String HTTPCLIENT_PROXY_AUTODETECT = "httpclient.proxy-autodetect";
    public static final String HTTPCLIENT_PROXY_HOST = "httpclient.proxy-host";
    public static final String HTTPCLIENT_PROXY_PORT = "httpclient.proxy-port";
    public static final String HTTPCLIENT_PROXY_USER = "httpclient.proxy-user";
    public static final String HTTPCLIENT_PROXY_PASSWORD = "httpclient.proxy-password";
    public static final String STORAGE_SERVICE_INTERNAL_ERROR_RETRY_MAX = "storage-service.internal-error-retry-max";

    private RestS3Service s3Service;
    private final S3ConnectionProperties s3ConnectionProperties;

    public S3Client(RestS3Service restS3Service, S3ConnectionProperties s3ConnectionProperties) {
        this.s3ConnectionProperties = s3ConnectionProperties;
        this.s3Service = restS3Service;
    }

    public S3Client(AwsCredentials awsCredentials, S3ConnectionProperties s3ConnectionProperties) {
        this.s3ConnectionProperties = s3ConnectionProperties;
        AWSCredentials credentials = awsCredentials == null ? null : new AWSCredentials(awsCredentials.getAccessKey(), awsCredentials.getSecretKey());
        try {
            s3Service = new RestS3Service(credentials, null, null, createConnectionProperties());
        } catch (S3ServiceException e) {
            throw new S3Exception("Could instantiate S3 Service", e);
        }
    }

    private Jets3tProperties createConnectionProperties() {
        Jets3tProperties properties = Jets3tProperties.getInstance(Constants.JETS3T_PROPERTIES_FILENAME);

        Optional<URI> endpoint = s3ConnectionProperties.getEndpoint();
        if (endpoint.isPresent()) {
            URI uri = endpoint.get();
            properties.setProperty(S3SERVICE_S3_ENDPOINT, uri.getHost());
            properties.setProperty(S3SERVICE_S3_ENDPOINT_HTTP_PORT, Integer.toString(uri.getPort()));
            properties.setProperty(S3SERVICE_HTTPS_ONLY, Boolean.toString(uri.getScheme().toUpperCase().equals("HTTPS")));
            properties.setProperty(S3SERVICE_DISABLE_DNS_BUCKETS, "true");
            properties.setProperty(STORAGE_SERVICE_INTERNAL_ERROR_RETRY_MAX, "0");
        }
        Optional<HttpProxySettings.HttpProxy> proxyOptional = s3ConnectionProperties.getProxy();
        if (proxyOptional.isPresent()) {
            HttpProxySettings.HttpProxy proxy = proxyOptional.get();
            properties.setProperty(HTTPCLIENT_PROXY_AUTODETECT, "false");
            properties.setProperty(HTTPCLIENT_PROXY_HOST, proxy.host);
            properties.setProperty(HTTPCLIENT_PROXY_PORT, Integer.toString(proxy.port));

            PasswordCredentials credentials = proxy.credentials;

            if (credentials != null) {
                properties.setProperty(HTTPCLIENT_PROXY_USER, credentials.getUsername());
                properties.setProperty(HTTPCLIENT_PROXY_PASSWORD, credentials.getPassword());
            }
        } else {
            properties.setProperty(HTTPCLIENT_PROXY_AUTODETECT, "true");
        }
        return properties;
    }

    public void put(InputStream inputStream, Long contentLength, URI destination) {

        try {
            String bucketName = getBucketName(destination);
            String s3BucketKey = getS3BucketKey(destination);
            S3Object object = new S3Object(s3BucketKey);
            object.setContentLength(contentLength);
            object.setDataInputStream(inputStream);
            LOGGER.debug("Attempting to put resource:[{}] into s3 bucket [{}]", s3BucketKey, bucketName);
            s3Service.putObject(bucketName, object);
        } catch (S3ServiceException e) {
            throw new S3Exception(String.format("Could not put s3 resource: [%s]. %s", destination.toString(), e.getErrorMessage()), e);
        }
    }

    public StorageObject getMetaData(URI uri) {
        LOGGER.debug("Attempting to get s3 meta-data: [{}]", uri.toString());
        String bucketName = getBucketName(uri);
        String s3Key = getS3BucketKey(uri);
        try {
            return s3Service.getObjectDetails(bucketName, s3Key);
        } catch (ServiceException e) {
            if(e.getResponseCode() == 404) {
                return null;
            }
            throw new S3Exception(String.format("Could not get s3 meta-data: [%s]. %s", uri.toString(), e.getErrorMessage()), e);
        }
    }

    public S3Object getResource(URI uri) {
        LOGGER.debug("Attempting to get s3 resource: [{}]", uri.toString());
        try {
            String bucketName = getBucketName(uri);
            return s3Service.getObject(bucketName, getS3BucketKey(uri));
        } catch (S3ServiceException e) {
            if(e.getResponseCode() == 404) {
                return null;
            }
            throw new S3Exception(String.format("Could not get s3 resource: [%s]. %s", uri.toString(), e.getErrorMessage()), e);
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
        try {
            S3Object[] s3Objects = s3Service.listObjects(bucketName, s3Key, "/");
            results.addAll(resolveResourceNames(s3Objects));
        } catch (S3ServiceException e) {
            if(e.getResponseCode() != 404) {
                throw new S3Exception(String.format("Could not list s3 resources for '%s'.", parent.toString()), e);
            }
        }
        return results;
    }

    private List<String> resolveResourceNames(S3Object[] s3Objects) {
        List<String> results = new ArrayList<String>();
        for (S3Object objectSummary : s3Objects) {
            String key = objectSummary.getKey();
            String fileName = extractResourceName(key);
            if (null != fileName) {
                results.add(fileName);
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
