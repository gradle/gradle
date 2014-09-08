/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.internal.resource.transport;


import org.gradle.internal.Factory;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.hash.HashUtil;
import org.gradle.internal.hash.HashValue;
import org.gradle.internal.resource.ExternalResource;
import org.gradle.internal.resource.metadata.ExternalResourceMetaData;
import org.gradle.internal.resource.transfer.ExternalResourceAccessor;
import org.gradle.internal.resource.transfer.ExternalResourceLister;
import org.gradle.internal.resource.transfer.ExternalResourceUploader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.util.List;

public class DefaultExternalResourceRepository implements ExternalResourceRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultExternalResourceRepository.class);
    private final String name;
    private final ExternalResourceAccessor accessor;
    private final ExternalResourceUploader uploader;
    private final ExternalResourceLister lister;

    public DefaultExternalResourceRepository(String name, ExternalResourceAccessor accessor, ExternalResourceUploader uploader,
                                             ExternalResourceLister lister) {
        this.name = name;
        this.accessor = accessor;
        this.uploader = uploader;
        this.lister = lister;
    }

    public ExternalResource getResource(URI source) throws IOException {
        return accessor.getResource(source);
    }

    public ExternalResourceMetaData getResourceMetaData(URI source) throws IOException {
        return accessor.getMetaData(source);
    }

    public void put(File source, URI destination) throws IOException {
        doPut(source, destination);
        putChecksum("SHA1", 40, source, destination);
    }

    private void putChecksum(String algorithm, int checksumlength, File source, URI destination) throws IOException {
        byte[] checksumFile = createChecksumFile(source, algorithm, checksumlength);
        URI checksumDestination = URI.create(destination + "." + algorithm.toLowerCase());
        doPut(checksumFile, checksumDestination);
    }

    private byte[] createChecksumFile(File src, String algorithm, int checksumlength) {
        HashValue hash = HashUtil.createHash(src, algorithm);
        String formattedHashString = formatHashString(hash.asHexString(), checksumlength);
        try {
            return formattedHashString.getBytes("US-ASCII");
        } catch (UnsupportedEncodingException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private String formatHashString(String hashKey, int length) {
        while (hashKey.length() < length) {
            hashKey = "0" + hashKey;
        }
        return hashKey;
    }

    private void doPut(final File source, URI destination) throws IOException {
        LOGGER.debug("Attempting to put resource {}.", destination);
        assert source.isFile();
        uploader.upload(new Factory<InputStream>() {
            public InputStream create() {
                try {
                    return new FileInputStream(source);
                } catch (FileNotFoundException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
        }, source.length(), destination);
    }

    private void doPut(final byte[] source, URI destination) throws IOException {
        LOGGER.debug("Attempting to put resource {}.", destination);
        uploader.upload(new Factory<InputStream>() {
            public InputStream create() {
                return new ByteArrayInputStream(source);
            }
        }, (long)source.length, destination);
    }

    public List<String> list(URI parent) throws IOException {
        return lister.list(parent);
    }

    public String toString() {
        return name;
    }
}
