/*
 * Copyright 2026 the original author or authors.
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

package gradlebuild.binarycompatibility

import gradlebuild.binarycompatibility.rules.SinceAnnotationRule
import japicmp.model.JApiClass
import me.champeau.gradle.japicmp.report.AbstractContextAwareViolationRule
import me.champeau.gradle.japicmp.report.Severity
import me.champeau.gradle.japicmp.report.ViolationCheckContext
import spock.lang.Specification
import spock.lang.TempDir

class NonPublicSinceAnnotationRuleTest extends Specification {

    private final static String CURRENT_VERSION = '11.38'
    private final static String CLASS_NAME = 'org.gradle.internal.Foo'

    @TempDir
    File tmp
    BinaryCompatibilityRepository repository

    def setup() {
        repository = BinaryCompatibilityRepository.openRepositoryFor([tmp])
    }

    def cleanup() {
        repository?.close()
    }

    def "@since is not required"() {
        given:
        sourceFor("public class Foo {}")

        expect:
        nonPublicRule().maybeViolation(classStub()) == null
    }

    def "a present @since must use the current version"() {
        given:
        sourceFor("""
            /**
             * @since $version
             */
            public class Foo {}
        """)

        when:
        def violation = nonPublicRule().maybeViolation(classStub())

        then:
        (violation == null) == expectedOk
        violation == null || (violation.severity == Severity.error && violation.humanExplanation =~ 'Has invalid @since')

        where:
        version         | expectedOk
        CURRENT_VERSION | true
        '4.0'           | false
    }

    def "an unsupported japicmp member type is skipped rather than failing"() {
        expect:
        nonPublicRule().maybeViolation(Stub(japicmp.model.JApiImplementedInterface)) == null
    }

    private JApiClass classStub() {
        def stub = Stub(JApiClass)
        stub.fullyQualifiedName >> CLASS_NAME
        return stub
    }

    private void sourceFor(String text) {
        def file = new File(tmp, CLASS_NAME.replace('.', '/') + ".java")
        file.parentFile.mkdirs()
        file.text = text
    }

    private AbstractContextAwareViolationRule nonPublicRule() {
        withContext(new SinceAnnotationRule([sinceRequired: false]))
    }

    private AbstractContextAwareViolationRule withContext(AbstractContextAwareViolationRule rule) {
        rule.context = new ViolationCheckContext() {

            @Override
            String getClassName() { CLASS_NAME }

            @Override
            Map<String, ?> getUserData() {
                [
                    currentVersion: CURRENT_VERSION,
                    (BinaryCompatibilityRepositorySetupRule.REPOSITORY_CONTEXT_KEY): repository
                ]
            }

            @Override
            <T> T getUserData(String key) {
                getUserData()[key]
            }

            @Override
            <T> void putUserData(String key, T value) {
                getUserData().put(key, value)
            }
        }
        rule
    }
}
