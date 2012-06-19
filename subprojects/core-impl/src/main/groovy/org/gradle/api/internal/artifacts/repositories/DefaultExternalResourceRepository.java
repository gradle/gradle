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
import org.apache.ivy.plugins.repository.AbstractRepository;
import org.apache.ivy.plugins.repository.BasicResource;
import org.apache.ivy.plugins.repository.RepositoryCopyProgressListener;
import org.apache.ivy.plugins.repository.TransferEvent;
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

public class DefaultExternalResourceRepository extends AbstractRepository implements ExternalResourceRepository {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultExternalResourceRepository.class);
    private final RepositoryCopyProgressListener progress = new RepositoryCopyProgressListener(this);
    private final TemporaryFileProvider temporaryFileProvider = new TmpDirTemporaryFileProvider();

    private final ExternalResourceAccessor accessor;
    private final ExternalResourceUploader uploader;
    private final ExternalResourceLister lister;

    private final CacheAwareExternalResourceAccessor cacheAwareAccessor;

    public DefaultExternalResourceRepository(String name, ExternalResourceAccessor accessor, ExternalResourceUploader uploader, ExternalResourceLister lister) {
        setName(name);
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
        fireTransferInitiated(resource, TransferEvent.REQUEST_GET);
        try {
            progress.setTotalLength(resource.getContentLength() > 0 ? resource.getContentLength() : null);
            resource.writeTo(destination, progress);
        } catch (IOException e) {
            fireTransferError(e);
            throw e;
        } catch (Exception e) {
            fireTransferError(e);
            throw UncheckedException.throwAsUncheckedException(e);
        } finally {
            progress.setTotalLength(null);
            resource.close();
        }
    }

    @Override
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

    @Override
    protected void put(File source, String destination, boolean overwrite) throws IOException {
        LOGGER.debug("Attempting to put resource {}.", destination);
        assert source.isFile();
        fireTransferInitiated(new BasicResource(destination, true, source.length(), source.lastModified(), false), TransferEvent.REQUEST_PUT);
        try {
            progress.setTotalLength(source.length());
            uploader.upload(source, destination, overwrite);
        } catch (IOException e) {
            fireTransferError(e);
            throw e;
        } catch (Exception e) {
            fireTransferError(e);
            throw UncheckedException.throwAsUncheckedException(e);
        } finally {
            progress.setTotalLength(null);
        }
    }

    public List list(String parent) throws IOException {
        return lister.list(parent);
    }
}
