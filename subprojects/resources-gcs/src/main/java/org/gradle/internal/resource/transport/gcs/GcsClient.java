/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.resource.transport.gcs;

import com.google.api.client.http.InputStreamContent;
import com.google.api.services.storage.Storage;
import com.google.api.services.storage.model.Objects;
import com.google.api.services.storage.model.StorageObject;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.common.annotations.VisibleForTesting;
import org.gradle.internal.resource.ResourceExceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GcsClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(GcsClient.class);
    private static final Pattern FILENAME_PATTERN = Pattern.compile("[^\\/]+\\.*$");
    private static final StorageObject INITIAL_METADATA = new StorageObject();

    private final Storage googleGcsClient;

    public GcsClient(Storage googleGcsClient) throws GeneralSecurityException, IOException {
        this.googleGcsClient = googleGcsClient;
    }

    public void put(InputStream inputStream, Long contentLength, URI destination) {
        try {
            InputStreamContent contentStream = new InputStreamContent(null, inputStream);
            // Setting the length improves upload performance
            contentStream.setLength(contentLength);

            // TODO - set ACL here if necessary
            String bucket = destination.getHost();
            String path = cleanResourcePath(destination);
            StorageObject objectMetadata = new StorageObject().setName(path);

            Storage.Objects.Insert putRequest = googleGcsClient.objects().insert(bucket, objectMetadata, contentStream);

            LOGGER.debug("Attempting to put resource:[{}] into gcs bucket [{}]", putRequest.getName(), putRequest.getBucket());
            putRequest.execute();
        } catch (IOException e) {
            throw ResourceExceptions.putFailed(destination, e);
        }
    }

    public StorageObject getMetaData(URI uri) {
        LOGGER.debug("Attempting to get gcs meta-data: [{}]", uri.toString());

        String path = cleanResourcePath(uri);
        try {
            Storage.Objects.Get getRequest = googleGcsClient.objects().get(uri.getHost(), path);
            return getRequest.execute();
        } catch (IOException e) {
            throw ResourceExceptions.getFailed(uri, e);
        }
    }

    public StorageObject getResource(URI uri) {
        LOGGER.debug("Attempting to get gcs resource: [{}]", uri.toString());

        String path = cleanResourcePath(uri);
        try {
            Storage.Objects.Get getRequest = googleGcsClient.objects().get(uri.getHost(), path);
            return getRequest.execute();
        } catch (GoogleJsonResponseException e) {
            // To bootstrap the very first publication to Maven, we have to insert a maven-metadata.xml
            // file. Gradle always tries to read-modify-write this file, so if its not there on the first
            // publish we'll fail and never be able to publish
            if (e.getStatusCode() == 404 && path.endsWith("maven-metadata.xml")) {
                return INITIAL_METADATA;
            }
            throw ResourceExceptions.getFailed(uri, e);
        } catch (IOException e) {
            throw ResourceExceptions.getFailed(uri, e);
        }
    }

    InputStream getResourceStream(StorageObject obj) throws IOException {
        if (obj == INITIAL_METADATA) {
            // To bootstrap the very first publication to Maven, we have to insert a maven-metadata.xml
            // file. Gradle always tries to read-modify-write this file, so if its not there on the first
            // publish we'll fail and never be able to publish
            return new ByteArrayInputStream("<metadata/>".getBytes());
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Storage.Objects.Get getObject = googleGcsClient.objects().get(obj.getBucket(), obj.getName());
        getObject.getMediaHttpDownloader().setDirectDownloadEnabled(false);
        getObject.executeMediaAndDownloadTo(baos);
        return new ByteArrayInputStream(baos.toByteArray());
    }

    public List<String> list(URI uri) {
        List<StorageObject> results = new ArrayList<StorageObject>();

        try {
            Storage.Objects.List listRequest = googleGcsClient.objects().list(uri.getHost());
            Objects objects;

            // Iterate through each page of results, and add them to our results list.
            do {
                objects = listRequest.execute();
                // Add the items in this page of results to the list we'll return.
                results.addAll(objects.getItems());

                // Get the next page, in the next iteration of this loop.
                listRequest.setPageToken(objects.getNextPageToken());
            } while (null != objects.getNextPageToken());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        List<String> resultStrings = new ArrayList<String>();
        for(StorageObject result : results) {
            resultStrings.add(result.getName());
        }

        return resultStrings;
    }

    @VisibleForTesting
    static String extractResourceName(String key) {
        Matcher matcher = FILENAME_PATTERN.matcher(key);
        if (matcher.find()) {
            String group = matcher.group(0);
            return group.contains(".") ? group : null;
        }
        return null;
    }

    private static String cleanResourcePath(URI uri) {
        String path;
        try {
            path = URLDecoder.decode(uri.getPath(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e); // fail fast, this should not happen
        }
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        return path;
    }
}
