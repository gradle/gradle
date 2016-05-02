/*
 * Copyright 2014 the original author or authors.
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
import org.gradle.api.Transformer;
import org.gradle.internal.FileUtils;
import org.gradle.internal.UncheckedException;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;

/**
 * A minimal resolver, which does not use any native services. Used during bootstrap only. You should generally use {@link FileResolver} instead.
 *
 * TODO - share more stuff with AbstractFileResolver.
 */
public class BasicFileResolver implements Transformer<File, String> {
    private static final Pattern URI_SCHEME = Pattern.compile("[a-zA-Z][a-zA-Z0-9+-\\.]*:.+");
    private final File baseDir;

    public BasicFileResolver(File baseDir) {
        this.baseDir = baseDir;
    }

    public File transform(String original) {
        if (original.startsWith("file:")) {
            try {
                return FileUtils.canonicalize(new File(new URI(original)));
            } catch (URISyntaxException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        }

        File file = new File(original);
        if (file.isAbsolute()) {
            return FileUtils.canonicalize(file);
        }

        if (URI_SCHEME.matcher(original).matches()) {
            throw new InvalidUserDataException(String.format("Cannot convert URL '%s' to a file.", original));
        }

        file = new File(baseDir, original);
        return FileUtils.canonicalize(file);
    }
}
