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

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.storage.Storage;

import java.io.IOException;
import java.io.FileInputStream;
import java.security.GeneralSecurityException;
import java.util.Arrays;


// Defer GcsClient creation
public class GcsClientFactory {

    // Get from system property because gradle forks and can't read the host environment properly
    private final static String SYSTEM_PROPERTY = "GOOGLE_APPLICATION_CREDENTIALS";
    private static volatile GcsClient gcsClient;

    public GcsClient newGcsClient() throws GeneralSecurityException, IOException {

        if (gcsClient == null) {

            HttpTransport transport = GoogleNetHttpTransport.newTrustedTransport();
            JsonFactory jsonFactory = new JacksonFactory();

            // Get the users credential, or use a service account if explicitly provided
            GoogleCredential googleCredential = null;
            String fileName = System.getProperty(SYSTEM_PROPERTY);
            if (fileName == null) {
                googleCredential = GoogleCredential.getApplicationDefault();
            } else {
                googleCredential = GoogleCredential.fromStream(new FileInputStream(fileName), transport, jsonFactory);
            }
            // Ensure we have a scope
            googleCredential = googleCredential.createScoped(Arrays.asList("https://www.googleapis.com/auth/devstorage.read_write"));

            HttpRequestInitializer initializer = new RetryHttpInitializerWrapper(googleCredential);
            Storage.Builder builder = new Storage.Builder(transport, jsonFactory, initializer);
            builder.setApplicationName("gradle");
            gcsClient = new GcsClient(builder.build());

        }

        return gcsClient;

    }
        
}
