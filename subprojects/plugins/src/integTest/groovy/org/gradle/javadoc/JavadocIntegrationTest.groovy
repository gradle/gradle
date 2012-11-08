/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.javadoc

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.TestResources
import org.junit.Rule
import spock.lang.Issue

class JavadocIntegrationTest extends AbstractIntegrationSpec {
    @Rule TestResources testResources = new TestResources()

    @Issue("GRADLE-1563")
    def handlesTagsAndTaglets() {
        when:
        run("javadoc")

        then:
        def javadoc = testResources.dir.file("build/docs/javadoc/Person.html")
        javadoc.text =~ /(?ms)This is the Person class.*Author.*author value.*Deprecated.*deprecated value.*Custom Tag.*custom tag value/
        // we can't currently control the order between tags and taglets (limitation on our side)
        javadoc.text =~ /(?ms)Custom Taglet.*custom taglet value/
    }

    @Issue("GRADLE-2520")
    def canCombineLocalOptionWithOtherOptions() {
        when:
        run("javadoc")

        then:
        def javadoc = testResources.dir.file("build/docs/javadoc/Person.html")
        javadoc.text =~ /(?ms)USED LOCALE=de_DE/
        javadoc.text =~ /(?ms)Serial no. is valid javadoc!/
    }
}
