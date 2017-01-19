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

package org.gradle.internal.resource;

import org.apache.commons.io.IOUtils;
import org.gradle.api.Nullable;
import org.gradle.api.resources.MissingResourceException;
import org.gradle.internal.SystemProperties;
import org.gradle.internal.resource.core.CharacterEncodingUtil;
import org.gradle.internal.resource.core.UserAgentBuilder;
import org.gradle.util.GradleVersion;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.Charset;

/**
 * A {@link TextResource} implementation backed by a URI. Assumes content is encoded using UTF-8.
 */
public class UriTextResource implements TextResource {
    private final File sourceFile;
    private final URI sourceUri;
    private final String description;

    public UriTextResource(String description, File sourceFile) {
        this.description = description;
        this.sourceFile = canonicalise(sourceFile);
        this.sourceUri = sourceFile.toURI();
    }

    private File canonicalise(File file) {
        try {
            return file.getCanonicalFile();
        } catch (IOException e) {
            return file.getAbsoluteFile();
        }
    }

    public UriTextResource(String description, URI sourceUri) {
        this.description = description;
        this.sourceFile = sourceUri.getScheme().equals("file") ? canonicalise(new File(sourceUri.getPath())) : null;
        this.sourceUri = sourceUri;
    }

    @Override
    public String getDisplayName() {
        StringBuilder builder = new StringBuilder();
        builder.append(description);
        builder.append(" '");
        builder.append(sourceFile != null ? sourceFile.getAbsolutePath() : sourceUri);
        builder.append("'");
        return builder.toString();
    }

    @Override
    public boolean isContentCached() {
        return false;
    }

    @Override
    public boolean getHasEmptyContent() {
        Reader reader = getAsReader();
        try {
            try {
                return reader.read() == -1;
            } finally {
                reader.close();
            }
        } catch (Exception e) {
            throw ResourceExceptions.failure(sourceUri, String.format("Could not read %s.", getDisplayName()), e);
        }
    }

    @Override
    public String getText() {
        Reader reader = getAsReader();
        try {
            try {
                return IOUtils.toString(reader);
            } finally {
                reader.close();
            }
        } catch (Exception e) {
            throw ResourceExceptions.failure(sourceUri, String.format("Could not read %s.", getDisplayName()), e);
        }
    }

    @Override
    public Reader getAsReader() {
        if (sourceFile != null && sourceFile.isDirectory()) {
            throw new ResourceIsAFolderException(sourceUri, String.format("Could not read %s as it is a directory.", getDisplayName()));
        }
        try {
            return getInputStream(sourceUri);
        } catch (FileNotFoundException e) {
            throw new MissingResourceException(sourceUri, String.format("Could not read %s as it does not exist.", getDisplayName()));
        } catch (Exception e) {
            throw ResourceExceptions.failure(sourceUri, String.format("Could not read %s.", getDisplayName()), e);
        }
    }

    @Override
    public boolean getExists() {
        try {
            Reader reader = getInputStream(sourceUri);
            try {
                return true;
            } finally {
                reader.close();
            }
        } catch (FileNotFoundException e) {
            return false;
        } catch (Exception e) {
            throw ResourceExceptions.failure(sourceUri, String.format("Could not determine if %s exists.", getDisplayName()), e);
        }
    }

    private Reader getInputStream(URI url) throws IOException {
        final URLConnection urlConnection = url.toURL().openConnection();
        urlConnection.setRequestProperty("User-Agent", UserAgentBuilder.getUserAgentString());

        // Without this, the URLConnection will keep the backing Jar file open indefinitely
        // This will have a performance impact for Jar-backed `UriTextResource` instances
        if (urlConnection instanceof JarURLConnection) {
            urlConnection.setUseCaches(false);
        }
        urlConnection.connect();
        String charset = CharacterEncodingUtil.extractCharacterEncoding(urlConnection.getContentType(), "utf-8");
        return new InputStreamReader(urlConnection.getInputStream(), charset);
    }

    @Override
    public File getFile() {
        return sourceFile != null && sourceFile.isFile() ? sourceFile : null;
    }

    @Override
    public Charset getCharset() {
        if (getFile() != null) {
            return Charset.forName("utf-8");
        }
        return null;
    }

    @Override
    public ResourceLocation getLocation() {
        return new UriResourceLocation();
    }

    private class UriResourceLocation implements ResourceLocation {
        @Override
        public String getDisplayName() {
            return UriTextResource.this.getDisplayName();
        }

        @Nullable
        @Override
        public File getFile() {
            return sourceFile;
        }

        @Nullable
        @Override
        public URI getURI() {
            return sourceUri;
        }
    }
}
