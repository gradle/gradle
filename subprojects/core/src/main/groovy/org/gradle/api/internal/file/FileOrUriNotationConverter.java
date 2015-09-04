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
import org.gradle.internal.exceptions.DiagnosticsVisitor;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.typeconversion.*;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileOrUriNotationConverter implements NotationConverter<Object, Object> {

    private static final Pattern URI_SCHEME = Pattern.compile("[a-zA-Z][a-zA-Z0-9+-\\.]*:.+");
    private static final Pattern ENCODED_URI = Pattern.compile("%([0-9a-fA-F]{2})");
    private final FileSystem fileSystem;

    public FileOrUriNotationConverter(FileSystem fileSystem) {
        this.fileSystem = fileSystem;
    }

    public static NotationParser<Object, Object> parser(FileSystem fileSystem) {
        return NotationParserBuilder
                .toType(Object.class)
                .typeDisplayName("a File or URI")
                .converter(new FileOrUriNotationConverter(fileSystem))
                .toComposite();
    }

    @Override
    public void describe(DiagnosticsVisitor visitor) {
        visitor.candidate("A String or CharSequence path").example("'src/main/java' or '/usr/include'");
        visitor.candidate("A String or CharSequence URI").example("'file:/usr/include'");
        visitor.candidate("A File instance.");
        visitor.candidate("A URI or URL instance.");
    }

    public void convert(Object notation, NotationConvertResult<? super Object> result) throws TypeConversionException {
        if (notation instanceof File) {
            result.converted(notation);
            return;
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
                result.converted(new File(uri.getPath()));
            } else {
                result.converted(uri);
            }
            return;
        }
        if (notation instanceof CharSequence) {
            String notationString = notation.toString();
            if (notationString.startsWith("file:")) {
                result.converted(new File(uriDecode(notationString.substring(5))));
                return;
            }
            for (File file : File.listRoots()) {
                String rootPath = file.getAbsolutePath();
                String normalisedStr = notationString;
                if (!fileSystem.isCaseSensitive()) {
                    rootPath = rootPath.toLowerCase();
                    normalisedStr = normalisedStr.toLowerCase();
                }
                if (normalisedStr.startsWith(rootPath) || normalisedStr.startsWith(rootPath.replace(File.separatorChar, '/'))) {
                    result.converted(new File(notationString));
                    return;
                }
            }
            // Check if string starts with a URI scheme
            if (URI_SCHEME.matcher(notationString).matches()) {
                try {
                    result.converted(new URI(notationString));
                    return;
                } catch (URISyntaxException e) {
                    throw new UncheckedIOException(e);
                }
            }
            result.converted(new File(notationString));
        }
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
