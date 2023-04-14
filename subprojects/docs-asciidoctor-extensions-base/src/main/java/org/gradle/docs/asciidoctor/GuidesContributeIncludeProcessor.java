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
import org.asciidoctor.extension.IncludeProcessor;
import org.asciidoctor.extension.PreprocessorReader;

import java.util.HashMap;
import java.util.Map;

public class GuidesContributeIncludeProcessor extends IncludeProcessor {
    private static final String CONTRIBUTE = "contribute";

    // Even though these are unused, these constructors are necessary to prevent
    // "(ArgumentError) asciidoctor: FAILED: Failed to load AsciiDoc document - wrong number of arguments (1 for 0)"
    // See https://github.com/asciidoctor/asciidoctorj/issues/451#issuecomment-210914940
    // This is fixed in asciidoctorj 1.6.0
    public GuidesContributeIncludeProcessor() {
        super(new HashMap<>());
    }

    public GuidesContributeIncludeProcessor(Map<String, Object> config) {
        super(config);
    }

    @Override
    public boolean handles(String target) {
        return target.equals(CONTRIBUTE);
    }

    // This method adheres the asciidoctorj 1.6.0 API
    public void process(Document document, PreprocessorReader reader, String target, Map<String, Object> attributes) {
        final String contributeMessage = getContributeMessage((String) attributes.getOrDefault("guide-name", document.getAttributes().get("guide-name")));
        reader.push_include(contributeMessage, target, target, 1, attributes);
    }

    private String issueUrl(String guideName) {
        if (guideName == null) {
            return "https://github.com/gradle/guides/issues/new";
        }
        return String.format("https://github.com/gradle/guides/issues/new?labels=in:%s", guideName);
    }

    private String repositoryUrl(String guideName) {
        if (guideName == null) {
            return "https://github.com/gradle/guides";
        }
        return String.format("https://github.com/gradle/guides/tree/master/subprojects/%s", guideName);
    }

    private String getContributeMessage(String guideName) {
        return String.format("%n[.contribute]%n== Help improve this guide%n%nHave feedback or a question? Found a typo? Like all Gradle guides, help is just a GitHub issue away. Please %s[add an issue] or pull request to %s[gradle/guides] and we'll get back to you.", issueUrl(guideName), repositoryUrl(guideName));
    }
}
