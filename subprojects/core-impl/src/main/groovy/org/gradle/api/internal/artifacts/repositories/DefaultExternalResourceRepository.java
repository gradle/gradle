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


import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.plugins.repository.*;
import org.gradle.api.internal.externalresource.ExternalResource;
import org.gradle.api.internal.externalresource.cached.CachedExternalResource;
import org.gradle.api.internal.externalresource.local.LocallyAvailableResourceCandidates;
import org.gradle.api.internal.externalresource.metadata.ExternalResourceMetaData;
import org.gradle.api.internal.externalresource.transfer.*;
import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.api.internal.file.TmpDirTemporaryFileProvider;
import org.gradle.internal.UncheckedException;
import org.gradle.util.GFileUtils;
import org.gradle.util.hash.HashUtil;
import org.gradle.util.hash.HashValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class DefaultExternalResourceRepository implements ExternalResourceRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultExternalResourceRepository.class);
    private final TemporaryFileProvider temporaryFileProvider = new TmpDirTemporaryFileProvider();

    private String name;
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

    public ExternalResource getResource(String source, LocallyAvailableResourceCandidates localCandidates, CachedExternalResource cached) throws IOException{
        return cacheAwareAccessor.getResource(source, localCandidates, cached);
    }

    public ExternalResourceMetaData getResourceMetaData(String source) throws IOException {
        return accessor.getMetaData(source);
    }

    public void get(String source, File destination) throws IOException {
        throw new UnsupportedOperationException();
    }

    public void downloadResource(ExternalResource resource, File destination) throws IOException {
        try {
            resource.writeTo(destination);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        } finally {
            resource.close();
        }
    }

    public void put(Artifact artifact, File source, String destination, boolean overwrite) throws IOException {
        put(source, destination, overwrite);
        putChecksum("SHA1", source, destination, overwrite);
    }

    private void putChecksum(String algorithm, File source, String destination, boolean overwrite) throws IOException {
        File checksumFile = createChecksumFile(source, algorithm);
        String checksumDestination = destination + "." + algorithm.toLowerCase();
        put(checksumFile, checksumDestination, overwrite);
    }

    private File createChecksumFile(File src, String algorithm) {
        File csFile = temporaryFileProvider.createTemporaryFile("ivytemp", algorithm);
        HashValue hash = HashUtil.createHash(src, algorithm);
        GFileUtils.writeFile(hash.asHexString(), csFile);
        return csFile;
    }

    protected void put(File source, String destination, boolean overwrite) throws IOException {
        LOGGER.debug("Attempting to put resource {}.", destination);
        assert source.isFile();
        try {
            uploader.upload(source, destination, overwrite);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    public List list(String parent) throws IOException {
        return lister.list(parent);
    }

    public void addTransferListener(TransferListener listener) {
        throw new UnsupportedOperationException("addTransferListener(TransferListener) is not implemented");
    }

    public void removeTransferListener(TransferListener listener) {
        throw new UnsupportedOperationException("removeTransferListener(TransferListener) is not implemented");
    }

    public boolean hasTransferListener(TransferListener listener) {
        throw new UnsupportedOperationException("hasTransferListener(TransferListener) is not implemented");
    }

    /**
     * followed methods are taken from AbstractRepository for compatibility reasons. Revisit them later.
     * */
    public String getFileSeparator() {
        return "/";
    }

    public String standardize(String source) {
        return source.replace('\\', '/');
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String toString() {
        return getName();
    }
}
