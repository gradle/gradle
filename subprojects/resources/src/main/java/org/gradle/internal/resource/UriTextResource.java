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
import org.gradle.api.resources.MissingResourceException;
import org.gradle.internal.FileUtils;
import org.gradle.internal.SystemProperties;
import org.gradle.util.GradleVersion;

import javax.annotation.Nullable;
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
 * A {@link TextResource} implementation backed by a URI. Defaults content encoding to UTF-8.
 */
public class UriTextResource implements TextResource {

    protected static final String DEFAULT_ENCODING = "utf-8";

    private final File sourceFile;
    private final URI sourceUri;
    private final String description;
    private String displayName;

    public UriTextResource(String description, File sourceFile) {
        this.description = description;
        this.sourceFile = FileUtils.canonicalize(sourceFile);
        this.sourceUri = sourceFile.toURI();
    }

    UriTextResource(String description, URI sourceUri) {
        this.description = description;
        this.sourceFile = sourceUri.getScheme().equals("file") ? FileUtils.canonicalize(new File(sourceUri.getPath())) : null;
        this.sourceUri = sourceUri;
    }

    @Override
    public String getDisplayName() {
        if (displayName == null) {
            StringBuilder builder = new StringBuilder();
            builder.append(description);
            builder.append(" '");
            builder.append(sourceFile != null ? sourceFile.getAbsolutePath() : sourceUri);
            builder.append("'");
            displayName = builder.toString();
        }
        return displayName;
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
            return openReader();
        } catch (FileNotFoundException e) {
            throw new MissingResourceException(sourceUri, String.format("Could not read %s as it does not exist.", getDisplayName()));
        } catch (Exception e) {
            throw ResourceExceptions.failure(sourceUri, String.format("Could not read %s.", getDisplayName()), e);
        }
    }

    @Override
    public boolean getExists() {
        try {
            Reader reader = openReader();
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

    protected Reader openReader() throws IOException {
        final URLConnection urlConnection = sourceUri.toURL().openConnection();
        urlConnection.setRequestProperty("User-Agent", getUserAgentString());

        // Without this, the URLConnection will keep the backing Jar file open indefinitely
        // This will have a performance impact for Jar-backed `UriTextResource` instances
        if (urlConnection instanceof JarURLConnection) {
            urlConnection.setUseCaches(false);
        }
        urlConnection.connect();
        String contentType = urlConnection.getContentType();
        String charset = extractCharacterEncoding(contentType, DEFAULT_ENCODING);
        return new InputStreamReader(urlConnection.getInputStream(), charset);
    }

    @Override
    public File getFile() {
        return sourceFile != null && sourceFile.isFile() ? sourceFile : null;
    }

    @Override
    public Charset getCharset() {
        if (getFile() != null) {
            return Charset.forName(DEFAULT_ENCODING);
        }
        return null;
    }

    @Override
    public ResourceLocation getLocation() {
        return new UriResourceLocation();
    }

    public static String extractCharacterEncoding(String contentType, String defaultEncoding) {
        if (contentType == null) {
            return defaultEncoding;
        }
        int pos = findFirstParameter(0, contentType);
        if (pos == -1) {
            return defaultEncoding;
        }
        StringBuilder paramName = new StringBuilder();
        StringBuilder paramValue = new StringBuilder();
        pos = findNextParameter(pos, contentType, paramName, paramValue);
        while (pos != -1) {
            if (paramName.toString().equals("charset") && paramValue.length() > 0) {
                return paramValue.toString();
            }
            pos = findNextParameter(pos, contentType, paramName, paramValue);
        }
        return defaultEncoding;
    }

    private static int findFirstParameter(int pos, String contentType) {
        int index = contentType.indexOf(';', pos);
        if (index < 0) {
            return -1;
        }
        return index + 1;
    }

    private static int findNextParameter(int pos, String contentType, StringBuilder paramName, StringBuilder paramValue) {
        if (pos >= contentType.length()) {
            return -1;
        }
        paramName.setLength(0);
        paramValue.setLength(0);
        int separator = contentType.indexOf("=", pos);
        if (separator < 0) {
            separator = contentType.length();
        }
        paramName.append(contentType.substring(pos, separator).trim());
        if (separator >= contentType.length() - 1) {
            return contentType.length();
        }

        int startValue = separator + 1;
        int endValue;
        if (contentType.charAt(startValue) == '"') {
            startValue++;
            int i = startValue;
            while (i < contentType.length()) {
                char ch = contentType.charAt(i);
                if (ch == '\\' && i < contentType.length() - 1 && contentType.charAt(i + 1) == '"') {
                    paramValue.append('"');
                    i += 2;
                } else if (ch == '"') {
                    break;
                } else {
                    paramValue.append(ch);
                    i++;
                }
            }
            endValue = i + 1;
        } else {
            endValue = contentType.indexOf(';', startValue);
            if (endValue < 0) {
                endValue = contentType.length();
            }
            paramValue.append(contentType.substring(startValue, endValue));
        }
        if (endValue < contentType.length() && contentType.charAt(endValue) == ';') {
            endValue++;
        }
        return endValue;
    }

    public static String getUserAgentString() {
        String osName = System.getProperty("os.name");
        String osVersion = System.getProperty("os.version");
        String osArch = System.getProperty("os.arch");
        String javaVendor = System.getProperty("java.vendor");
        String javaVersion = SystemProperties.getInstance().getJavaVersion();
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

        @Override
        public URI getURI() {
            return sourceUri;
        }
    }
}
