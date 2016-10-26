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
package org.gradle.internal.resource.transport.file;

import org.apache.commons.io.IOUtils;
import org.gradle.internal.resource.local.DefaultLocallyAvailableExternalResource;
import org.gradle.internal.resource.ExternalResource;
import org.gradle.internal.resource.local.LocallyAvailableExternalResource;
import org.gradle.internal.resource.local.DefaultLocallyAvailableResource;
import org.gradle.internal.resource.local.LocalResource;
import org.gradle.internal.resource.metadata.ExternalResourceMetaData;
import org.gradle.internal.resource.transport.ExternalResourceRepository;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.List;

public class FileResourceConnector implements ExternalResourceRepository {
    @Override
    public ExternalResourceRepository withProgressLogging() {
        return this;
    }

    public List<String> list(URI parent) {
        File dir = getFile(parent);
        if (dir.exists() && dir.isDirectory()) {
            String[] names = dir.list();
            if (names != null) {
                return Arrays.asList(names);
            }
        }
        return null;
    }

    @Override
    public void put(LocalResource source, URI destination) throws IOException {
        File target = getFile(destination);
        if (!target.canWrite()) {
            target.delete();
        } // if target is writable, the copy will overwrite it without requiring a delete
        GFileUtils.mkdirs(target.getParentFile());

        InputStream input = source.open();
        try {
            FileOutputStream output = new FileOutputStream(target);
            try {
                IOUtils.copyLarge(input, output);
            } finally {
                output.close();
            }
        } finally {
            input.close();
        }
    }

    public LocallyAvailableExternalResource getResource(URI uri, boolean revalidate) {
        File localFile = getFile(uri);
        if (!localFile.exists()) {
            return null;
        }
        return new DefaultLocallyAvailableExternalResource(uri, new DefaultLocallyAvailableResource(localFile));
    }

    public ExternalResourceMetaData getResourceMetaData(URI location, boolean revalidate) {
        ExternalResource resource = getResource(location, revalidate);
        return resource == null ? null : resource.getMetaData();
    }

    private static File getFile(URI uri) {
        return new File(uri);
    }
}
