/*
 * Copyright 2018 the original author or authors.
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
import org.asciidoctor.extension.Postprocessor;

import java.util.Map;

/**
 * Injects static assets for docs
 */
public class HeaderInjectingPostprocessor extends Postprocessor {
    private final String headerHtml;

    HeaderInjectingPostprocessor(Map<String, Object> config, String headerHtml) {
        super(config);
        this.headerHtml = headerHtml;
    }

    /**
     * Inject common header before page title.
     */
    @Override
    public String process(Document document, String output) {
        if (!document.isBasebackend("html")) {
            return output;
        }
        return output.replaceAll("<div id=\"header\">", headerHtml + "<div id=\"header\">");
    }

    // This method is necessary to avoid https://github.com/asciidoctor/asciidoctorj-pdf/issues/7
    // when generating PDFs.
    // "(TypeError) cannot convert instance of class org.jruby.gen.RubyObject50 to class java.lang.String"
    public Object process(Object document, Object output) {
        return output;
    }
}
