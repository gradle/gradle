/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.build.docs.dsl.docbook;

import org.gradle.build.docs.dsl.docbook.model.ClassDoc;
import org.w3c.dom.Element;

public class ClassDocRenderer {
    private final GenerationListener listener = new DefaultGenerationListener();
    private final ClassDescriptionRenderer descriptionRenderer = new ClassDescriptionRenderer();
    private final PropertiesRenderer propertiesRenderer;
    private final MethodsRenderer methodsRenderer;
    final BlocksRenderer blocksRenderer;

    public ClassDocRenderer(LinkRenderer linkRenderer) {
        propertiesRenderer = new PropertiesRenderer(linkRenderer, listener);
        methodsRenderer = new MethodsRenderer(linkRenderer, listener);
        blocksRenderer = new BlocksRenderer(linkRenderer, listener);
    }

    public void mergeContent(ClassDoc classDoc, Element parent) {
        listener.start(String.format("class %s", classDoc.getName()));
        try {
            Element chapter = parent.getOwnerDocument().createElement("chapter");
            parent.appendChild(chapter);
            chapter.setAttribute("id", classDoc.getId());
            descriptionRenderer.renderTo(classDoc, chapter);
            mergeProperties(classDoc, chapter);
            mergeBlocks(classDoc, chapter);
            mergeMethods(classDoc, chapter);
        } finally {
            listener.finish();
        }
    }

    void mergeProperties(ClassDoc classDoc, Element classContent) {
        propertiesRenderer.renderTo(classDoc, classContent);
    }

    void mergeMethods(ClassDoc classDoc, Element classContent) {
        methodsRenderer.renderTo(classDoc, classContent);
    }

    void mergeBlocks(ClassDoc classDoc, Element classContent) {
        blocksRenderer.renderTo(classDoc, classContent);
    }
}
