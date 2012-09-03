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

package org.gradle.api.internal.artifacts.repositories;


import org.gradle.api.internal.externalresource.ExternalResource;
import org.gradle.api.internal.externalresource.cached.CachedExternalResource;
import org.gradle.api.internal.externalresource.local.LocallyAvailableResourceCandidates;
import org.gradle.api.internal.externalresource.metadata.ExternalResourceMetaData;
import org.gradle.api.internal.externalresource.transfer.*;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.api.internal.file.TmpDirTemporaryFileProvider;
import org.gradle.internal.Factory;
import org.gradle.internal.UncheckedException;
import org.gradle.util.GFileUtils;
import org.gradle.util.hash.HashUtil;
import org.gradle.util.hash.HashValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.math.BigInteger;
import java.util.List;

public class DefaultExternalResourceRepository implements ExternalResourceRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultExternalResourceRepository.class);
    private final TemporaryFileProvider temporaryFileProvider = new TmpDirTemporaryFileProvider();

    private final String name;
    private final ExternalResourceAccessor accessor;
    private final ExternalResourceUploader uploader;
    private final ExternalResourceLister lister;

    private final CacheAwareExternalResourceAccessor cacheAwareAccessor;

    public DefaultExternalResourceRepository(String name, ExternalResourceAccessor accessor, ExternalResourceUploader uploader, ExternalResourceLister lister) {
        this.name = name;
        this.accessor = accessor;
        this.uploader = uploader;
        this.lister = lister;

        this.cacheAwareAccessor = new DefaultCacheAwareExternalResourceAccessor(accessor);
    }

    public ExternalResource getResource(String source) throws IOException {
        return accessor.getResource(source);
    }

    public ExternalResource getResource(String source, LocallyAvailableResourceCandidates localCandidates, CachedExternalResource cached) throws IOException {
        return cacheAwareAccessor.getResource(source, localCandidates, cached);
    }

    public ExternalResourceMetaData getResourceMetaData(String source) throws IOException {
        return accessor.getMetaData(source);
    }

    public void put(File source, String destination) throws IOException {
        doPut(source, destination);
        putChecksum("SHA1", "%040x", source, destination);
    }

    private void putChecksum(String algorithm, String format, File source, String destination) throws IOException {
        File checksumFile = createChecksumFile(source, algorithm, format);
        String checksumDestination = destination + "." + algorithm.toLowerCase();
        doPut(checksumFile, checksumDestination);
    }

    private File createChecksumFile(File src, String algorithm, String format) {
        File csFile = temporaryFileProvider.createTemporaryFile("ivytemp", algorithm);
        HashValue hash = HashUtil.createHash(src, algorithm);
        final String formattedHash = String.format(format, new BigInteger(1, hash.asByteArray()));
        GFileUtils.writeFile(formattedHash, csFile, "US-ASCII");
        return csFile;
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
