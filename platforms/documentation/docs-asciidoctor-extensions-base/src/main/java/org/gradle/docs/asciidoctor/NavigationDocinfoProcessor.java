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
import org.asciidoctor.extension.DocinfoProcessor;

import java.util.Map;

class NavigationDocinfoProcessor extends DocinfoProcessor {
    private final String html;

    NavigationDocinfoProcessor(Map<String, Object> config, String html) {
        super(config);
        this.html = html;
    }

    @Override
    public String process(Document document) {
        return html;
    }
}
