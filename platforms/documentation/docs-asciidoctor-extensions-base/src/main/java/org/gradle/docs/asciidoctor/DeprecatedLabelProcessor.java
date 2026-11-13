/*
 * Copyright 2026 the original author or authors.
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

import org.asciidoctor.ast.PhraseNode;
import org.asciidoctor.ast.StructuralNode;
import org.asciidoctor.extension.Format;
import org.asciidoctor.extension.InlineMacroProcessor;
import org.asciidoctor.extension.Name;

import java.util.HashMap;
import java.util.Map;

import static org.asciidoctor.extension.FormatType.SHORT;

/**
 * Processes {@code deprecated-label} inline macros to add deprecation badges.
 * <p>
 * Usage: {@code deprecated-label:[]}
 * <p>
 * This creates a small badge indicating that something is deprecated, for example
 * next to a Gradle property or command-line option that is scheduled for removal.
 */
@Name("deprecated-label")
@Format(SHORT)
public class DeprecatedLabelProcessor extends InlineMacroProcessor {

    @Override
    public PhraseNode process(StructuralNode parent, String target, Map<String, Object> attributes) {
        String html = "<span class=\"deprecated-label\">Deprecated</span>";
        return createPhraseNode(parent, "quoted", html, attributes, new HashMap<>());
    }
}
