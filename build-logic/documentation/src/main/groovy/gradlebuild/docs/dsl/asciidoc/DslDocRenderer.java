/*
 * Copyright 2021 the original author or authors.
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

package gradlebuild.docs.dsl.asciidoc;

import gradlebuild.docs.dsl.source.model.ClassMetaData;
import groovy.lang.Writable;
import groovy.text.SimpleTemplateEngine;
import groovy.text.Template;

import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

public class DslDocRenderer {
    public DslDocRenderer() {
//        memberRenderers.add(new PropertiesRenderer(linkRenderer, listener));
//        memberRenderers.add(new MethodsRenderer(linkRenderer, listener));
//        memberRenderers.add(new BlocksRenderer(linkRenderer, listener));
    }

    public void mergeContent(ClassMetaData classMetaData, Writer parent) throws IOException, ClassNotFoundException {
        Template template = new SimpleTemplateEngine().createTemplate(getClass().getResource("class_template.adoc"));
        Map<Object, Object> options = new HashMap<>();
        options.put("classMetadata", classMetaData);
        Writable result = template.make(options);
        result.writeTo(parent);
    }

//    void merge(ClassDoc classDoc, Element chapter) {
//        for (ClassDocMemberRenderer memberRenderer : memberRenderers) {
//            memberRenderer.renderSummaryTo(classDoc, chapter);
//        }
//        for (ClassDocMemberRenderer memberRenderer : memberRenderers) {
//            memberRenderer.renderDetailsTo(classDoc, chapter);
//        }
//    }
}
