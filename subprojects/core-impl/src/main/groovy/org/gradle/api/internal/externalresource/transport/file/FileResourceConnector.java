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
package org.gradle.api.internal.externalresource.transport.file;

import org.apache.ivy.util.FileUtil;
import org.gradle.api.internal.externalresource.ExternalResource;
import org.gradle.api.internal.externalresource.LocallyAvailableExternalResource;
import org.gradle.api.internal.externalresource.local.DefaultLocallyAvailableResource;
import org.gradle.api.internal.externalresource.metadata.ExternalResourceMetaData;
import org.gradle.api.internal.externalresource.transfer.ExternalResourceAccessor;
import org.gradle.api.internal.externalresource.transfer.ExternalResourceLister;
import org.gradle.api.internal.externalresource.transfer.ExternalResourceUploader;
import org.gradle.util.hash.HashValue;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FileResourceConnector implements ExternalResourceLister, ExternalResourceAccessor, ExternalResourceUploader {

    public List<String> list(String parent) throws IOException {
        File dir = getFile(parent);
        if (dir.exists() && dir.isDirectory()) {
            String[] names = dir.list();
            if (names != null) {
                List<String> ret = new ArrayList<String>(names.length);
                for (String name : names) {
                    ret.add(parent + '/' + name);
                }
                return ret;
            }
        }
        return null;
    }

    public void upload(File source, String destination, boolean overwrite) throws IOException {
        File target = getFile(destination);
        if (!overwrite && target.exists()) {
            throw new IOException("Could not copy file " + source + " to " + target + ": target already exists and overwrite is false");
        }
        if (!FileUtil.copy(source, target, null, overwrite)) {
            throw new IOException("File copy failed from " + source + " to " + target);
        }
    }

    public ExternalResource getResource(String location) throws IOException {
        File localFile = getFile(location);
        if (!localFile.exists()) {
            return null;
        }
        return new LocallyAvailableExternalResource(location, new DefaultLocallyAvailableResource(localFile));
    }

    public ExternalResourceMetaData getMetaData(String location) throws IOException {
        ExternalResource resource = getResource(location);
        return resource == null ? null : resource.getMetaData();
    }

    public HashValue getResourceSha1(String location) {
        // TODO Read sha1 from published .sha1 file
        return null;
    }

    private static File getFile(String absolutePath) {
        File f = new File(absolutePath);
        if (!f.isAbsolute()) {
            throw new IllegalArgumentException("Filename must be absolute: " + absolutePath);
        }
        return f;
    }
}
