/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.problems.internal;

import org.gradle.api.problems.DocLink;
import org.jetbrains.annotations.Nullable;

/**
 * Simple implementation of {@link DocLink}.
 * <p>
 * This class is capable of holding a URL and a message detailing how to consult the documentation.
 */
public class DefaultDocLink implements DocLink {

    private final String url;
    private final String message;

    public DefaultDocLink(String url, String message) {
        this.url = url;
        this.message = message;
    }

    @Nullable
    @Override
    public String getUrl() {
        return url;
    }

    @Nullable
    @Override
    public String getConsultDocumentationMessage() {
        return message;
    }
}
