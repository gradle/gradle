/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.buildinit.plugins.internal

import groovy.text.SimpleTemplateEngine
import groovy.text.Template

class SimpleTemplateOperation implements TemplateOperation {
    private final URL templateURL
    private final File target
    private final Map<String, TemplateValue> bindings

    public SimpleTemplateOperation(URL templateURL, File target, Map<String, TemplateValue>  bindings) {
        if (templateURL == null) {
            throw new BuildInitException("Template URL must not be null")
        }
        if (target == null) {
            throw new BuildInitException("Target file must not be null")
        }
        this.templateURL = templateURL
        this.bindings = bindings
        this.target = target
    }

    void generate() {
        target.parentFile.mkdirs()
        SimpleTemplateEngine templateEngine = new SimpleTemplateEngine()
        Template template = templateEngine.createTemplate(templateURL.text)
        target.withWriter("utf-8") { writer ->
            template.make(bindings).writeTo(writer)
        }
    }
}
