/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.docs.asciidoctor;

import org.asciidoctor.ast.ContentNode;
import org.asciidoctor.ast.PhraseNode;
import org.asciidoctor.extension.InlineMacroProcessor;
import org.asciidoctor.extension.Name;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Processes since-gradle-label inline macros to add version information badges.
 * <p>
 * Usage: {@code since-gradle-label:9.1.0[]}
 * <p>
 * This creates a small badge indicating since which Gradle version a feature is available.
 */
@Name("since-gradle-label")
public class SinceGradleLabelProcessor extends InlineMacroProcessor {

    @Override
    public @Nullable PhraseNode process(ContentNode parent, String target, Map<String, Object> attributes) {
        String version = target;
        if (version == null || version.trim().isEmpty()) {
            return null;
        }

        String html = "<span class=\"since-gradle-label\">Since " + version + "</span>";

        return createPhraseNode(parent, "quoted", html, attributes, new HashMap<>());
    }
}
