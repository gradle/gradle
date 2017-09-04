/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.resource.transport.gcp.gcs;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.storage.Storage;
import com.google.api.services.storage.model.Objects;
import com.google.api.services.storage.model.StorageObject;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.resources.ResourceException;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.resource.ResourceExceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.singletonList;

public class GcsClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(GcsClient.class);

    private final Storage storage;

    public static GcsClient create(GcsConnectionProperties gcsConnectionProperties) throws GeneralSecurityException, IOException {
        HttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();
        JsonFactory jsonFactory = new JacksonFactory();
        Storage.Builder builder = new Storage.Builder(transport, jsonFactory, null);
        if (gcsConnectionProperties.requiresAuthentication()) {
            Supplier<Credential> credentialSupplier = getCredentialSupplier(transport, jsonFactory);
            builder.setHttpRequestInitializer(new RetryHttpInitializerWrapper(credentialSupplier));
        }
        if (gcsConnectionProperties.getEndpoint().isPresent()) {
            builder.setRootUrl(gcsConnectionProperties.getEndpoint().get().toString());
        }
        if (gcsConnectionProperties.getServicePath().isPresent()) {
            builder.setServicePath(gcsConnectionProperties.getServicePath().get());
        }
        builder.setApplicationName("gradle");
        return new GcsClient(builder.build());
    }

    @VisibleForTesting
    GcsClient(Storage storage) {
        this.storage = storage;
    }

    public void put(InputStream inputStream, Long contentLength, URI destination) throws ResourceException {
        try {
            InputStreamContent contentStream = new InputStreamContent(null, inputStream);
            // Setting the length improves upload performance
            contentStream.setLength(contentLength);

            // TODO - set ACL here if necessary
            String bucket = destination.getHost();
            String path = cleanResourcePath(destination);
            StorageObject objectMetadata = new StorageObject().setName(path);

            Storage.Objects.Insert putRequest = storage.objects().insert(bucket, objectMetadata, contentStream);

            LOGGER.debug("Attempting to put resource:[{}] into gcs bucket [{}]", putRequest.getName(), putRequest.getBucket());
            putRequest.execute();
        } catch (IOException e) {
            throw ResourceExceptions.putFailed(destination, e);
        }
    }

    @Nullable
    public StorageObject getResource(URI uri) throws ResourceException {
        LOGGER.debug("Attempting to get gcs resource: [{}]", uri.toString());

        String path = cleanResourcePath(uri);
        try {
            Storage.Objects.Get getRequest = storage.objects().get(uri.getHost(), path);
            return getRequest.execute();
        } catch (GoogleJsonResponseException e) {
            // When an artifact is being published it is first checked whether it is available.
            // If a transport returns `null` then it is assumed that artifact does not exist.
            // If we throw, an attempt to publish will fail altogether even if we use ResourceExceptions#getMissing(uri).
            if (e.getStatusCode() == 404) {
                return null;
            }
            throw ResourceExceptions.getFailed(uri, e);
        } catch (IOException e) {
            throw ResourceExceptions.getFailed(uri, e);
        }
    }

    @Nullable
    public List<String> list(URI uri) throws ResourceException {
        List<StorageObject> results = new ArrayList<StorageObject>();

        try {
            Storage.Objects.List listRequest = storage.objects().list(uri.getHost());
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
            throw ResourceExceptions.getFailed(uri, e);
        }

        List<String> resultStrings = new ArrayList<String>();
        for(StorageObject result : results) {
            resultStrings.add(result.getName());
        }

        return resultStrings;
    }

    @VisibleForTesting
    InputStream getResourceStream(StorageObject obj) throws IOException {
        Storage.Objects.Get getObject = storage.objects().get(obj.getBucket(), obj.getName());
        getObject.getMediaHttpDownloader().setDirectDownloadEnabled(false);
        return getObject.executeMediaAsInputStream();
    }

    private static String cleanResourcePath(URI uri) {
        String path;
        try {
            path = URLDecoder.decode(uri.getPath(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw UncheckedException.throwAsUncheckedException(e); // fail fast, this should not happen
        }
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        return path;
    }

    private static Supplier<Credential> getCredentialSupplier(final HttpTransport transport, final JsonFactory jsonFactory) {
        return Suppliers.memoize(new Supplier<Credential>() {
            @Override
            public Credential get() {
                try {
                    GoogleCredential googleCredential = GoogleCredential.getApplicationDefault(transport, jsonFactory);
                    // Ensure we have a scope
                    return googleCredential.createScoped(singletonList("https://www.googleapis.com/auth/devstorage.read_write"));
                } catch (IOException e) {
                    throw new UncheckedIOException("Failed to get Google credentials for GCS connection", e);
                }
            }
        });
    }
}
