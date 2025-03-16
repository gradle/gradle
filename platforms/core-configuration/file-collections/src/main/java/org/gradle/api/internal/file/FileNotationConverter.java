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

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.resources.TextResource;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.exceptions.DiagnosticsVisitor;
import org.gradle.internal.typeconversion.NotationConvertResult;
import org.gradle.internal.typeconversion.NotationConverter;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.internal.typeconversion.NotationParserBuilder;
import org.gradle.internal.typeconversion.TypeConversionException;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FileNotationConverter implements NotationConverter<Object, File> {
    private static final Pattern ENCODED_URI = Pattern.compile("%([0-9a-fA-F]{2})");

    public static NotationParser<Object, File> parser() {
        return NotationParserBuilder
            .toType(File.class)
            .typeDisplayName("a File")
            .noImplicitConverters()
            .converter(new FileNotationConverter())
            .toComposite();
    }

    @Override
    public void describe(DiagnosticsVisitor visitor) {
        visitor.candidate("A String or CharSequence path").example("'src/main/java' or '/usr/include'");
        visitor.candidate("A String or CharSequence URI").example("'file:/usr/include'");
        visitor.candidate("A File instance.");
        visitor.candidate("A Path instance.");
        visitor.candidate("A Directory instance.");
        visitor.candidate("A RegularFile instance.");
        visitor.candidate("A URI or URL instance of file.");
        visitor.candidate("A TextResource instance.");
    }

    @Override
    public void convert(Object notation, NotationConvertResult<? super File> result) throws TypeConversionException {
        if (notation instanceof File) {
            result.converted((File) notation);
            return;
        }
        if (notation instanceof Path) {
            result.converted(((Path) notation).toFile());
            return;
        }
        if (notation instanceof FileSystemLocation) {
            result.converted(((FileSystemLocation) notation).getAsFile());
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
            if ("file".equals(uri.getScheme())) {
                try {
                    result.converted(new File(uri));
                    return;
                } catch (IllegalArgumentException ex) {
                    throw new InvalidUserDataException(String.format("Cannot convert URI '%s' to a file.", uri), ex);
                }
            }
            return;
        }
        if (notation instanceof CharSequence) {
            String notationString = notation.toString();
            if (notationString.startsWith("file:")) {
                try {
                    URI uri = new URI(notationString);
                    try {
                        result.converted(new File(uri));
                        return;
                    } catch (IllegalArgumentException ignored) {
                        // Bad file URI, use old logic
                    }
                } catch (URISyntaxException ignored) {
                    // Unparsable, use old logic
                }
                // Fallback if relative or unparsable
                result.converted(new File(fallbackUrlDecode(notationString)));
                return;
            }
            result.converted(new File(notationString));
        }
        if (notation instanceof TextResource) {
            // TODO: This eagerly resolves a TextResource into a File, ignoring dependencies
            result.converted(((TextResource) notation).asFile());
        }
    }

    /**
     * Lenient legacy behavior to fall back to when URI cannot be normally parsed.
     */
    private static String fallbackUrlDecode(String fullPath) {
        DeprecationLogger.deprecateBehaviour("Passing invalid URIs to URI or File converting methods.")
            .withAdvice("Use a valid URL or a file path instead of '" + fullPath + "'.")
            .willBecomeAnErrorInGradle9()
            .withUpgradeGuideSection(8, "deprecated_invalid_url_decoding")
            .nagUser();
        String path = fullPath.substring(5);
        StringBuffer builder = new StringBuffer();
        Matcher matcher = ENCODED_URI.matcher(path);
        while (matcher.find()) {
            String val = matcher.group(1);
            matcher.appendReplacement(builder, String.valueOf((char) Integer.parseInt(val, 16)));
        }
        matcher.appendTail(builder);
        return builder.toString();
    }
}
