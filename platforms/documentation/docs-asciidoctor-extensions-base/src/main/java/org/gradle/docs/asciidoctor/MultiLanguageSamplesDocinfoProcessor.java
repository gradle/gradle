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

import org.apache.commons.io.IOUtils;
import org.asciidoctor.ast.Document;
import org.asciidoctor.extension.DocinfoProcessor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

class MultiLanguageSamplesDocinfoProcessor extends DocinfoProcessor {
    public MultiLanguageSamplesDocinfoProcessor() {
        super(new HashMap<>());
    }

    public MultiLanguageSamplesDocinfoProcessor(Map<String, Object> config) {
        super(config);
    }

    @Override
    public String process(Document document) {
        return "<style type=\"text/css\">" + readResourceContent("/multi-language-samples.css") + "</style>" +
                "<script type=\"text/javascript\">" + readResourceContent("/multi-language-samples.js") + "</script>";
    }

    private String readResourceContent(String resourcePath) {
        try (InputStream inputStream = MultiLanguageSamplesDocinfoProcessor.class.getResourceAsStream(resourcePath)) {
            return IOUtils.toString(inputStream, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read source resource for MultiLanguageSamples: " + e.getMessage());
        }
    }
}
