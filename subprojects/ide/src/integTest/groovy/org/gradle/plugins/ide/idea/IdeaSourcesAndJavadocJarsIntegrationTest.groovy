/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.plugins.ide.idea

import org.gradle.plugins.ide.AbstractSourcesAndJavadocJarsIntegrationTest

class IdeaSourcesAndJavadocJarsIntegrationTest extends AbstractSourcesAndJavadocJarsIntegrationTest {
    void executeIdeTask(String buildScript) {
        runTask "ideaModule", buildScript
    }

    void ideFileContainsSourcesAndJavadocEntry(String sourcesClassifier = "sources", String javadocClassifier = "javadoc") {
        def iml = parseFile("root.iml")

        def sourcesUrl = iml.component.orderEntry.library.SOURCES.root.@url[0].text()
        assert sourcesUrl.endsWith("/module-1.0-${sourcesClassifier}.jar!/")

        def javadocUrl = iml.component.orderEntry.library.JAVADOC.root.@url[0].text()
        assert javadocUrl.endsWith("/module-1.0-${javadocClassifier}.jar!/")
    }
}