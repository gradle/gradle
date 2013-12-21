/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.internal.resource;

import org.apache.commons.io.IOUtils;
import org.gradle.internal.SystemProperties;
import org.gradle.util.GradleVersion;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;

import static org.gradle.util.GFileUtils.canonicalise;

public class UriResource implements Resource {
    private final File sourceFile;
    private final URI sourceUri;
    private final String description;

    public UriResource(String description, File sourceFile) {
        this.description = description;
        this.sourceFile = canonicalise(sourceFile);
        this.sourceUri = sourceFile.toURI();
    }

    public UriResource(String description, URI sourceUri) {
        this.description = description;
        this.sourceFile = sourceUri.getScheme().equals("file") ? canonicalise(new File(sourceUri.getPath())) : null;
        this.sourceUri = sourceUri;
    }

    public String getDisplayName() {
        return String.format("%s '%s'", description, sourceFile != null ? sourceFile.getAbsolutePath() : sourceUri);
    }

    public String getText() {
        if (sourceFile != null && sourceFile.isDirectory()) {
            throw new ResourceException(String.format("Could not read %s as it is a directory.", getDisplayName()));
        }
        try {
            InputStream inputStream = getInputStream(sourceUri);
            try {
                return IOUtils.toString(inputStream);
            } finally {
                inputStream.close();
            }
        } catch (FileNotFoundException e) {
            throw new ResourceNotFoundException(String.format("Could not read %s as it does not exist.", getDisplayName()));
        } catch (Exception e) {
            throw new ResourceException(String.format("Could not read %s.", getDisplayName()), e);
        }
    }

    public boolean getExists() {
        try {
            InputStream inputStream = getInputStream(sourceUri);
            try {
                return true;
            } finally {
                inputStream.close();
            }
        } catch (FileNotFoundException e) {
            return false;
        } catch (Exception e) {
            throw new ResourceException(String.format("Could not determine if %s exists.", getDisplayName()), e);
        }
    }

    private InputStream getInputStream(URI url) throws IOException {
        final URLConnection urlConnection = url.toURL().openConnection();
        urlConnection.setRequestProperty("User-Agent", getUserAgentString());
        return urlConnection.getInputStream();
    }

    public File getFile() {
        return sourceFile;
    }

    public URI getURI() {
        return sourceUri;
    }

    public static String getUserAgentString() {
        String osName = System.getProperty("os.name");
        String osVersion = System.getProperty("os.version");
        String osArch = System.getProperty("os.arch");
        String javaVendor = System.getProperty("java.vendor");
        String javaVersion = SystemProperties.getJavaVersion();
        String javaVendorVersion = System.getProperty("java.vm.version");
        return String.format("Gradle/%s (%s;%s;%s) (%s;%s;%s)",
                GradleVersion.current().getVersion(),
                osName,
                osVersion,
                osArch,
                javaVendor,
                javaVersion,
                javaVendorVersion);
    }

}
