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

package org.gradle.docs.asciidoctor;

import org.asciidoctor.ast.Document;
import org.asciidoctor.ast.StructuralNode;
import org.asciidoctor.extension.Treeprocessor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Adds self-links to example block titles.
 */
public class ExampleSelfLinkProcessor extends Treeprocessor {

    private static final Map<Object, Object> EXAMPLE_SELECTOR = new HashMap<>();

    // common ID prefix (to avoid trivial cases of clashes where section name has the same id as the example title)
    private static final String ID_PREFIX = "ex-";

    static {
        EXAMPLE_SELECTOR.put("context", ":example");
    }

    @Override
    public Document process(Document document) {
        List<StructuralNode> examples = document.findBy(EXAMPLE_SELECTOR);
        for (StructuralNode example : examples) {
            if (example.hasAttribute("title")) {
                // Using attribute value, since it contains Asciidoc markup, as opposed to getTitle() that returns rendered html
                String title = example.getAttribute("title").toString();
                String exampleId = example.getId();
                if (exampleId == null) {
                    exampleId = IdGenerator.generateId(ID_PREFIX + title);
                    example.setId(exampleId);
                }
                // Using setTitle() instead of setAttribute(), because the latter has no effect
                example.setTitle(String.format("link:#%s[%s]", exampleId, title));
            }
        }
        return document;
    }
}
