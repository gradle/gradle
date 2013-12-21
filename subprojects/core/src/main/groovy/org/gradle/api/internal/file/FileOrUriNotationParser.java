/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.api.internal.file;

import org.gradle.api.UncheckedIOException;
import org.gradle.internal.nativeplatform.filesystem.FileSystem;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.util.DeprecationLogger;

import java.io.File;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileOrUriNotationParser<T extends Serializable> implements NotationParser<Object, T> {

    private static final Pattern URI_SCHEME = Pattern.compile("[a-zA-Z][a-zA-Z0-9+-\\.]*:.+");
    private static final Pattern ENCODED_URI = Pattern.compile("%([0-9a-fA-F]{2})");
    private final FileSystem fileSystem;

    public FileOrUriNotationParser(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }

    public void describe(Collection<String> candidateFormats) {
        candidateFormats.add("File, URI, URL or CharSequence is supported");
    }

    public T parseNotation(Object notation) {
        if (notation instanceof File) {
            return (T) notation;
        }
        if (notation instanceof URL) {
            try {
                notation = ((URL) notation).toURI();
            } catch (URISyntaxException e) {
                throw new UncheckedIOException(e);
            }
        }
        if (notation instanceof URI) {
            URI uri = (URI) notation;
            if (uri.getScheme().equals("file")) {
                return (T) new File(uri.getPath());
            } else {
                return (T) uri;
            }
        }
        if (notation instanceof CharSequence) {
            String notationString = notation.toString();
            if (notationString.startsWith("file:")) {
                return (T) new File(uriDecode(notationString.substring(5)));
            }
            for (File file : File.listRoots()) {
                String rootPath = file.getAbsolutePath();
                String normalisedStr = notationString;
                if (!fileSystem.isCaseSensitive()) {
                    rootPath = rootPath.toLowerCase();
                    normalisedStr = normalisedStr.toLowerCase();
                }
                if (normalisedStr.startsWith(rootPath) || normalisedStr.startsWith(rootPath.replace(File.separatorChar,
                        '/'))) {
                    return (T) new File(notationString);
                }
            }
            // Check if string starts with a URI scheme
            if (URI_SCHEME.matcher(notationString).matches()) {
                try {
                    return (T) new URI(notationString);
                } catch (URISyntaxException e) {
                    throw new UncheckedIOException(e);
                }
            }
        } else {
            DeprecationLogger.nagUserOfDeprecated(
                    String.format("Converting class %s to File using toString() method", notation.getClass().getName()),
                    "Please use java.io.File, java.lang.String, java.net.URL, or java.net.URI instead"
            );
        }
        return (T) new File(notation.toString());
    }

    private String uriDecode(String path) {
        StringBuffer builder = new StringBuffer();
        Matcher matcher = ENCODED_URI.matcher(path);
        while (matcher.find()) {
            String val = matcher.group(1);
            matcher.appendReplacement(builder, String.valueOf((char) (Integer.parseInt(val, 16))));
        }
        matcher.appendTail(builder);
        return builder.toString();
    }

}
