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

package org.gradle.api.internal.externalresource.transport;


import org.gradle.api.internal.externalresource.ExternalResource;
import org.gradle.api.internal.externalresource.local.LocallyAvailableResourceCandidates;
import org.gradle.api.internal.externalresource.metadata.ExternalResourceMetaData;
import org.gradle.api.internal.externalresource.transfer.CacheAwareExternalResourceAccessor;
import org.gradle.api.internal.externalresource.transfer.ExternalResourceAccessor;
import org.gradle.api.internal.externalresource.transfer.ExternalResourceLister;
import org.gradle.api.internal.externalresource.transfer.ExternalResourceUploader;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.internal.Factory;
import org.gradle.internal.UncheckedException;
import org.gradle.util.GFileUtils;
import org.gradle.internal.hash.HashUtil;
import org.gradle.internal.hash.HashValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.List;

public class DefaultExternalResourceRepository implements ExternalResourceRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultExternalResourceRepository.class);
    private final TemporaryFileProvider temporaryFileProvider;
    private final String name;
    private final ExternalResourceAccessor accessor;
    private final ExternalResourceUploader uploader;
    private final ExternalResourceLister lister;

    private final CacheAwareExternalResourceAccessor cacheAwareAccessor;

    public DefaultExternalResourceRepository(String name, ExternalResourceAccessor accessor, ExternalResourceUploader uploader,
                                             ExternalResourceLister lister, TemporaryFileProvider temporaryFileProvider,
                                             CacheAwareExternalResourceAccessor cacheAwareAccessor) {
        this.name = name;
        this.accessor = accessor;
        this.uploader = uploader;
        this.lister = lister;
        this.temporaryFileProvider = temporaryFileProvider;
        this.cacheAwareAccessor = cacheAwareAccessor;
    }

    public ExternalResource getResource(String source) throws IOException {
        return accessor.getResource(source);
    }

    public ExternalResource getResource(String source, LocallyAvailableResourceCandidates localCandidates) throws IOException {
        return cacheAwareAccessor.getResource(source, localCandidates);
    }

    public ExternalResourceMetaData getResourceMetaData(String source) throws IOException {
        return accessor.getMetaData(source);
    }

    public void put(File source, String destination) throws IOException {
        doPut(source, destination);
        putChecksum("SHA1", 40, source, destination);
    }

    private void putChecksum(String algorithm, int checksumlength, File source, String destination) throws IOException {
        File checksumFile = createChecksumFile(source, algorithm, checksumlength);
        String checksumDestination = destination + "." + algorithm.toLowerCase();
        doPut(checksumFile, checksumDestination);
    }

    private File createChecksumFile(File src, String algorithm, int checksumlength) {
        File csFile = temporaryFileProvider.createTemporaryFile("gradle_upload", algorithm);
        HashValue hash = HashUtil.createHash(src, algorithm);
        String formattedHashString = formatHashString(hash.asHexString(), checksumlength);
        GFileUtils.writeFile(formattedHashString, csFile, "US-ASCII");
        return csFile;
    }

    private String formatHashString(String hashKey, int length) {
        while (hashKey.length() < length) {
            hashKey = "0" + hashKey;
        }
        return hashKey;
    }

    protected void doPut(final File source, String destination) throws IOException {
        LOGGER.debug("Attempting to put resource {}.", destination);
        assert source.isFile();
        try {
            uploader.upload(new Factory<InputStream>() {
                public InputStream create() {
                    try {
                        return new FileInputStream(source);
                    } catch (FileNotFoundException e) {
                        throw UncheckedException.throwAsUncheckedException(e);
                    }
                }
            }, source.length(), destination);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    public List<String> list(String parent) throws IOException {
        return lister.list(parent);
    }

    public String toString() {
        return name;
    }
}
