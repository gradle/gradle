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
import org.gradle.internal.exceptions.DiagnosticsVisitor;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.typeconversion.NotationConvertResult;
import org.gradle.internal.typeconversion.NotationConverter;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.internal.typeconversion.NotationParserBuilder;
import org.gradle.internal.typeconversion.TypeConversionException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

public class UriNotationConverter implements NotationConverter<Object, URI> {

    public static NotationParser<Object, URI> parser() {
        return NotationParserBuilder
            .toType(URI.class)
            .typeDisplayName("a URI")
            .noImplicitConverters()
            .converter(new UriNotationConverter())
            .toComposite();
    }

    @Override
    public void describe(DiagnosticsVisitor visitor) {
        visitor.candidate("A URI or URL instance.");
        visitor.candidate("A String or CharSequence URI").example("\"http://www.gradle.org\"");
    }

    @Override
    public void convert(Object notation, NotationConvertResult<? super URI> result) throws TypeConversionException {
        if (notation instanceof URL) {
            try {
                notation = ((URL) notation).toURI();
            } catch (URISyntaxException e) {
                throw invalidUserDataException(notation, e);
            }
        }
        if (notation instanceof URI) {
            URI uri = (URI) notation;
            result.converted(uri);
            return;
        }
        if (notation instanceof CharSequence) {
            String notationString = notation.toString();
            if (notationString.startsWith("file:")) {
                return; // will be handled by file converter
            }
            if (notationString.startsWith("http://") || notationString.startsWith("https://")) {
                try {
                    result.converted(new URI(notationString));
                } catch (URISyntaxException e) {
                    throw invalidUserDataException(notationString, e);
                }
                return;
            }
            try {
                URI uri = new URI(notationString);
                if (uri.getScheme() == null || isWindowsRootDirectory(uri.getScheme())) {
                    return; // will be handled by file converter
                }
                result.converted(uri);
            } catch (URISyntaxException e) {
                // ignore, this is not a valid URI
            }
        }
    }

    @Nonnull
    private static InvalidUserDataException invalidUserDataException(Object notation, URISyntaxException e) {
        return new InvalidUserDataException(String.format("Cannot convert '%s' to a URI.", notation), e);
    }

    private static boolean isWindowsRootDirectory(@Nullable String scheme) {
        return scheme != null && scheme.length() == 1 && Character.isLetter(scheme.charAt(0)) && OperatingSystem.current().isWindows();
    }
}
