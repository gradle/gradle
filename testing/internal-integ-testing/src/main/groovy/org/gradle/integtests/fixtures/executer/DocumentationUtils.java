/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.integtests.fixtures.executer;

import org.gradle.util.GradleVersion;

import static org.gradle.api.internal.DocumentationRegistry.BASE_URL;
import static org.gradle.api.internal.DocumentationRegistry.BASE_URL_WITHOUT_VERSION;

public class DocumentationUtils {
    public static final String CURRENT_DOCS_URL = BASE_URL_WITHOUT_VERSION + "current/";

    public static String normalizeDocumentationLink(String message) {
        return message.replace(CURRENT_DOCS_URL, BASE_URL + "/");
    }

    public static String normalizeDocumentationLink(String message, GradleVersion version) {
        return message.replace(CURRENT_DOCS_URL, BASE_URL_WITHOUT_VERSION + version.getVersion() + "/");
    }
}
