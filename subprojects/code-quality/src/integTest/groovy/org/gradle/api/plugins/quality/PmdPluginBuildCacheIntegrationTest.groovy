/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.plugins.quality

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.DirectoryBuildCacheFixture
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

import static org.hamcrest.Matchers.containsString
import static org.hamcrest.Matchers.not

class PmdPluginBuildCacheIntegrationTest extends AbstractIntegrationSpec implements DirectoryBuildCacheFixture {

    def setup() {
        buildFile << """
            apply plugin: "java"
            apply plugin: "pmd"
            pmd {
                incrementalAnalysis = true
                ignoreFailures = true
            }
            ${mavenCentralRepository()}
        """.stripIndent()
    }

    @Requires(TestPrecondition.NOT_WINDOWS)
    def "pmd can work with build cache"() {
        given:
        // put into cache
        goodCode()
        withBuildCache().succeeds('pmdMain')

        when:
        // cache miss
        badCode()
        withBuildCache().succeeds('pmdMain')

        then:
        file("build/reports/pmd/main.xml").assertContents(containsString('BadClass'))

        when:
        // cache hit
        removeBadCode()
        withBuildCache().succeeds('pmdMain')

        then:
        file("build/reports/pmd/main.xml").assertContents(not(containsString('BadClass')))

        when:
        fixBadCode()
        withBuildCache().succeeds('pmdMain')

        then:
        file("build/reports/pmd/main.xml").assertContents(not(containsString('BadClass')))
    }

    private goodCode() {
        file("src/main/java/org/gradle/GoodClass.java") <<
            "package org.gradle; class GoodClass { public boolean isFoo(Object arg) { return true; } }"
    }

    private badCode() {
        // PMD Lvl 2 Warning BooleanInstantiation
        // PMD Lvl 3 Warning OverrideBothEqualsAndHashcode
        file("src/main/java/org/gradle/BadClass.java") <<
            "package org.gradle; class BadClass { public boolean equals(Object arg) { return java.lang.Boolean.valueOf(true); } }"
    }

    private fixBadCode() {
        file("src/main/java/org/gradle/BadClass.java") <<
            "package org.gradle; class BadClass { public boolean isFoo(Object arg) { return true; } }"
    }

    private removeBadCode() {
        boolean result = file("src/main/java/org/gradle/BadClass.java").delete()
        assert result
    }
}
