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

package org.gradle.jvm.internal.resolve;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.internal.artifacts.publish.DefaultPublishArtifact;

import java.io.File;

public class LibraryPublishArtifact extends DefaultPublishArtifact {

    public LibraryPublishArtifact(String type, File file) {
        super(determineName(file), determineExtension(file), type, null, null, file);
    }

    private static String determineExtension(File file) {
        return StringUtils.substringAfterLast(file.getName(), ".");
    }

    private static String determineName(File file) {
        return StringUtils.substringBeforeLast(file.getName(), ".");
    }
}
