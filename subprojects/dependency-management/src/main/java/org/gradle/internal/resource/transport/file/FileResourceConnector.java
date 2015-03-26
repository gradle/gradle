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
import org.gradle.internal.Factory;
import org.gradle.internal.resource.DefaultLocallyAvailableExternalResource;
import org.gradle.internal.resource.ExternalResource;
import org.gradle.internal.resource.LocallyAvailableExternalResource;
import org.gradle.internal.resource.local.DefaultLocallyAvailableResource;
import org.gradle.internal.resource.metadata.ExternalResourceMetaData;
import org.gradle.internal.resource.transfer.ExternalResourceConnector;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.List;

public class FileResourceConnector implements ExternalResourceConnector {
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

    public void upload(Factory<InputStream> source, Long contentLength, URI destination) throws IOException {
        File target = getFile(destination);
        if (!target.canWrite()) {
            target.delete();
        } // if target is writable, the copy will overwrite it without requiring a delete
        GFileUtils.mkdirs(target.getParentFile());
        FileOutputStream fileOutputStream = new FileOutputStream(target);
        try {
            InputStream sourceInputStream = source.create();
            try {
                IOUtils.copyLarge(sourceInputStream, fileOutputStream);
            } finally {
                sourceInputStream.close();
            }
        } finally {
            fileOutputStream.close();
        }
    }

    public LocallyAvailableExternalResource getResource(URI uri) {
        File localFile = getFile(uri);
        if (!localFile.exists()) {
            return null;
        }
        return new DefaultLocallyAvailableExternalResource(uri, new DefaultLocallyAvailableResource(localFile));
    }

    public ExternalResourceMetaData getMetaData(URI location) {
        ExternalResource resource = getResource(location);
        return resource == null ? null : resource.getMetaData();
    }

    private static File getFile(URI uri) {
        return new File(uri);
    }
}
