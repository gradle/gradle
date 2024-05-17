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

import static org.gradle.api.internal.DocumentationRegistry.BASE_URL;

public class DocumentationUtils {
    public static final String DOCS_GRADLE_ORG = "https://docs.gradle.org/";
    public static final String PATTERN = DOCS_GRADLE_ORG + "current/";

    public static String normalizeDocumentationLink(String message) {
        return message.replace(PATTERN, BASE_URL + "/");
    }
}
