/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.logging;

import org.gradle.internal.UncheckedException;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * Renders information in a format suitable for logging to the console.
 */
public class ConsoleRenderer {
    /**
     * Renders a path name as a file URL that is likely recognized by consoles.
     */
    public String asClickableFileUrl(File path) {
        // File.toURI().toString() leads to an URL like this on Mac: file:/reports/index.html
        // This URL is not recognized by the Mac console (too few leading slashes). We solve
        // this be creating an URI with an empty authority.
        try {
            return new URI("file", "", path.toURI().getPath(), null, null).toString();
        } catch (URISyntaxException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }
}
