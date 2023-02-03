/*
 * Copyright 2016 the original author or authors.
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

import java.util.HashMap;
import java.util.Map;

/**
 * This processor injects arbitrary HTML &lt;meta&gt; tags from document
 * attributes the pattern "meta_name-$NAME=$CONTENT" or
 * "meta_property-$PROPERTY=$CONTENT".
 *
 * For example the declaration ":meta-name-robots: noindex" would produce:
 * &lt;meta name="robots" content="noindex"&gt;
 *
 * Underscores will be replaced with colons. ":meta-property-og_locale: en_US"
 * would produce &lt;meta property="og:locale" content="en_US"&gt;
 */
public class MetadataDocinfoProcessor extends DocinfoProcessor {
    private static final String META_NAME = "meta-name-";
    private static final String META_PROPERTY = "meta-property-";

    public MetadataDocinfoProcessor() {
        this(new HashMap<>());
    }

    public MetadataDocinfoProcessor(Map<String, Object> config) {
        super(config);
    }

    @Override
    public String process(Document document) {
        StringBuilder outputHtml = new StringBuilder();
        Map<String, Object> attributes = document.getAttributes();

        for (Map.Entry<String, Object> attr : attributes.entrySet()) {
            String attributeKey = attr.getKey();
            if (attributeKey.startsWith(META_NAME)) {
                String name = attributeKey.substring(META_NAME.length()).replaceAll("_", ":");
                String content = attr.getValue().toString();
                outputHtml.append(String.format("<meta name=\"%s\" content=\"%s\">\n", name, content));
            } else if (attributeKey.startsWith(META_PROPERTY)) {
                String name = attributeKey.substring(META_PROPERTY.length()).replaceAll("_", ":");
                String content = attr.getValue().toString();
                outputHtml.append(String.format("<meta property=\"%s\" content=\"%s\">\n", name, content));
            }
        }

        return outputHtml.toString();
    }

    // This method is necessary to avoid https://github.com/asciidoctor/asciidoctorj-pdf/issues/7
    // when generating PDFs.
    // "(NameError) no method 'process' for arguments (org.jruby.RubyObject,org.jruby.RubyObject)"
    public Object process(Object document, Object output) {
        return output;
    }
}
