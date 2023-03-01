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

package org.gradle.groovy

import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.testing.fixture.GroovyCoverage
import org.gradle.util.internal.VersionNumber

import static org.gradle.util.internal.GroovyDependencyUtil.groovyModuleDependency
import static org.junit.Assume.assumeTrue

@TargetCoverage({ GroovyCoverage.SUPPORTS_GROOVYDOC })
class GroovyDocOptionsIntegrationTest extends MultiVersionIntegrationSpec {

    private static final String GROOVY_DOC_MAIN_PATTERN = /#main/
    private static final String GROOVY_DOC_PRIVATE_PATTERN = /#privateMethod/
    private static final String GROOVY_DOC_PACKAGE_PATTERN = /#packageMethod/
    private static final String GROOVY_DOC_PROTECTED_PATTERN = /#protectedMethod/
    private static final String GROOVY_DOC_PUBLIC_PATTERN = /#publicMethod/

    private static boolean supportsScriptsInGroovydoc() {
        return versionNumber >= VersionNumber.parse("1.7.3")
    }

    // The flags are available in all versions of Groovydoc, but package/protected only works in 1.7 and above
    private static boolean supportsHidingNonPrivateScopes() {
        return versionNumber >= VersionNumber.parse("1.7")
    }

    def setup() {
        buildFile << """
            plugins {
                id("groovy")
            }

            ${mavenCentralRepository()}

            dependencies {
                implementation "${groovyModuleDependency("groovy", versionNumber)}"
            }
        """

        file("src/main/groovy/options/Thing.java") << """
            package options;

            public class Thing {
                private void privateMethod(){}
                void packageMethod(){}
                protected void protectedMethod(){}
                public void publicMethod(){}
            }
        """

        file("src/main/groovy/Script.groovy") << """
            void someMethod() {}
        """
    }

    def "scripts are enabled and have main method by default"() {
        assumeTrue(supportsScriptsInGroovydoc())
        when:
        buildFile << "groovydoc {}"
        run "groovydoc"

        then:
        def doc = file('build/docs/groovydoc/DefaultPackage/Script.html')
        doc.exists()
        doc.text =~ GROOVY_DOC_MAIN_PATTERN
    }

    def "scripts can be disabled"() {
        assumeTrue(supportsScriptsInGroovydoc())
        when:
        buildFile << "groovydoc { processScripts = false }"
        run "groovydoc"

        then:
        def doc = file('build/docs/groovydoc/DefaultPackage/Script.html')
        !doc.exists()
    }

    def "main method can be disabled for scripts"() {
        assumeTrue(supportsScriptsInGroovydoc())
        when:
        buildFile << "groovydoc { includeMainForScripts = false }"
        run "groovydoc"

        then:
        def text = file('build/docs/groovydoc/DefaultPackage/Script.html').text
        !(text =~ GROOVY_DOC_MAIN_PATTERN)
    }

    def "public and protected scope are enabled by default"() {
        when:
        buildFile << "groovydoc {}"
        run "groovydoc"

        then:
        def text = file('build/docs/groovydoc/options/Thing.html').text
        !(text =~ GROOVY_DOC_PRIVATE_PATTERN)
        !supportsHidingNonPrivateScopes() || !(text =~ GROOVY_DOC_PACKAGE_PATTERN)
        text =~ GROOVY_DOC_PROTECTED_PATTERN
        text =~ GROOVY_DOC_PUBLIC_PATTERN
    }

    def "package scope can be enabled"() {
        when:
        buildFile << "groovydoc { access = GroovydocAccess.PACKAGE }"
        run "groovydoc"

        then:
        def text = file('build/docs/groovydoc/options/Thing.html').text
        !(text =~ GROOVY_DOC_PRIVATE_PATTERN)
        text =~ GROOVY_DOC_PACKAGE_PATTERN
        text =~ GROOVY_DOC_PROTECTED_PATTERN
        text =~ GROOVY_DOC_PUBLIC_PATTERN
    }

    def "private scope can be enabled"() {
        when:
        buildFile << "groovydoc { access = GroovydocAccess.PRIVATE }"
        run "groovydoc"

        then:
        def text = file('build/docs/groovydoc/options/Thing.html').text
        text =~ GROOVY_DOC_PRIVATE_PATTERN
        text =~ GROOVY_DOC_PACKAGE_PATTERN
        text =~ GROOVY_DOC_PROTECTED_PATTERN
        text =~ GROOVY_DOC_PUBLIC_PATTERN
    }

    def "can limit to only public members"() {
        when:
        buildFile << "groovydoc { access = GroovydocAccess.PUBLIC }"
        run "groovydoc"

        then:
        def text = file('build/docs/groovydoc/options/Thing.html').text
        !(text =~ GROOVY_DOC_PRIVATE_PATTERN)
        !supportsHidingNonPrivateScopes() || !(text =~ GROOVY_DOC_PACKAGE_PATTERN)
        !supportsHidingNonPrivateScopes() || !(text =~ GROOVY_DOC_PROTECTED_PATTERN)
        text =~ GROOVY_DOC_PUBLIC_PATTERN
    }
}
