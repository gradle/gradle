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

package org.gradle.docs.releasenotes

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import spock.lang.Shared
import spock.lang.Specification

abstract class AbstractReleaseNotesTest extends Specification {

    @Shared File sourceFile
    @Shared String sourceText

    @Shared File renderedFile
    @Shared Document renderedDocument
    @Shared String renderedText

    private static File getSysPropFile(String property) {
        def value = System.getProperty(property)
        assert value != null : "System property '$property' is not set"
        def file = new File(value)
        assert file.file : "File '$file' (from system property '$property') does not exist"
        file
    }

    def setupSpec() {
        sourceFile = getSysPropFile("org.gradle.docs.releasenotes.source")
        sourceText = sourceFile.getText("utf-8")
        renderedFile = getSysPropFile("org.gradle.docs.releasenotes.rendered")
        renderedText = renderedFile.getText("utf-8")
        renderedDocument = Jsoup.parse(renderedText)
    }

}
