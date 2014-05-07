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
import org.gradle.internal.SystemProperties;
import org.gradle.util.GradleVersion;

import java.io.*;
import java.net.URI;
import java.net.URLConnection;

/**
 * A {@link Resource} implementation backed by a URI. Assumes content is encoded using UTF-8.
 */
public class UriResource implements Resource {
    private final File sourceFile;
    private final URI sourceUri;
    private final String description;

    public UriResource(String description, File sourceFile) {
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
            Reader reader = getInputStream(sourceUri);
            try {
                return IOUtils.toString(reader);
            } finally {
                reader.close();
            }
        } catch (FileNotFoundException e) {
            throw new ResourceNotFoundException(String.format("Could not read %s as it does not exist.", getDisplayName()));
        } catch (Exception e) {
            throw new ResourceException(String.format("Could not read %s.", getDisplayName()), e);
        }
    }

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
            throw new ResourceException(String.format("Could not determine if %s exists.", getDisplayName()), e);
        }
    }

    private Reader getInputStream(URI url) throws IOException {
        final URLConnection urlConnection = url.toURL().openConnection();
        urlConnection.setRequestProperty("User-Agent", getUserAgentString());
        urlConnection.connect();
        String charset = extractCharacterEncoding(urlConnection.getContentType(), "utf-8");
        return new InputStreamReader(urlConnection.getInputStream(), charset);
    }

    public File getFile() {
        return sourceFile;
    }

    public URI getURI() {
        return sourceUri;
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
