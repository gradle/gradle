/*
 * Copyright 2020 the original author or authors.
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

package gradlebuild.docs.dsl.docbook;

import gradlebuild.docs.dsl.docbook.model.ClassDoc;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;

public class ClassDocRenderer {
    private final GenerationListener listener = new DefaultGenerationListener();
    private final ClassDescriptionRenderer descriptionRenderer = new ClassDescriptionRenderer();
    private final List<ClassDocMemberRenderer> memberRenderers = new ArrayList<ClassDocMemberRenderer>();

    public ClassDocRenderer(LinkRenderer linkRenderer) {
        memberRenderers.add(new PropertiesRenderer(linkRenderer, listener));
        memberRenderers.add(new MethodsRenderer(linkRenderer, listener));
        memberRenderers.add(new BlocksRenderer(linkRenderer, listener));
    }

    public void mergeContent(ClassDoc classDoc, Element parent) {
        listener.start(String.format("class %s", classDoc.getName()));
        try {
            Element chapter = parent.getOwnerDocument().createElement("chapter");
            parent.appendChild(chapter);
            chapter.setAttribute("id", classDoc.getId());
            descriptionRenderer.renderTo(classDoc, chapter);
            merge(classDoc, chapter);
        } finally {
            listener.finish();
        }
    }

    void merge(ClassDoc classDoc, Element chapter) {
        for (ClassDocMemberRenderer memberRenderer : memberRenderers) {
            memberRenderer.renderSummaryTo(classDoc, chapter);
        }
        for (ClassDocMemberRenderer memberRenderer : memberRenderers) {
            memberRenderer.renderDetailsTo(classDoc, chapter);
        }
    }
}
