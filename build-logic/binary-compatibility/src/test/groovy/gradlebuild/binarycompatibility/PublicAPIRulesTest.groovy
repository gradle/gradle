/*
 * Copyright 2020 the original author or authors.
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

import gradlebuild.binarycompatibility.rules.BinaryBreakingChangesRule
import gradlebuild.binarycompatibility.rules.IncubatingMissingRule
import gradlebuild.binarycompatibility.rules.NewIncubatingAPIRule
import gradlebuild.binarycompatibility.rules.SinceAnnotationMissingRule
import japicmp.model.JApiAnnotation
import japicmp.model.JApiClass
import japicmp.model.JApiCompatibility
import japicmp.model.JApiConstructor
import japicmp.model.JApiField
import japicmp.model.JApiMethod
import japicmp.model.JApiParameter
import me.champeau.gradle.japicmp.report.AbstractContextAwareViolationRule
import me.champeau.gradle.japicmp.report.Severity
import me.champeau.gradle.japicmp.report.ViolationCheckContext
import org.gradle.api.Incubating
import spock.lang.Specification
import spock.lang.TempDir

import javax.inject.Inject

class PublicAPIRulesTest extends Specification {
    private final static String TEST_INTERFACE_NAME = 'org.gradle.api.ApiTest'
    private final static String TEST_INTERFACE_SIMPLE_NAME = 'ApiTest'

    @TempDir
    File tmp
    File sourceFile

    BinaryCompatibilityRepository repository

    def jApiClassifier = Stub(JApiClass) // represents interfaces, enums and annotations
    def jApiMethod = Stub(JApiMethod)
    def jApiField = Stub(JApiField) // represents fields and enum literals
    def jApiConstructor = Stub(JApiConstructor)
    def incubatingAnnotation = Stub(JApiAnnotation)
    def deprecatedAnnotation = Stub(JApiAnnotation)
    def overrideAnnotation = Stub(JApiAnnotation)
    def injectAnnotation = Stub(JApiAnnotation)

    def setup() {
        new File(tmp, "org/gradle/api").mkdirs()
        sourceFile = new File(tmp, "${TEST_INTERFACE_NAME.replace('.', '/')}.java").tap { text = "" }

        jApiClassifier.fullyQualifiedName >> TEST_INTERFACE_NAME
        jApiField.name >> 'field'
        jApiField.jApiClass >> jApiClassifier
        jApiMethod.name >> 'method'
        jApiMethod.jApiClass >> jApiClassifier
        jApiConstructor.name >> 'ApiTest'
        jApiConstructor.jApiClass >> jApiClassifier

        incubatingAnnotation.fullyQualifiedName >> Incubating.name
        deprecatedAnnotation.fullyQualifiedName >> Deprecated.name
        overrideAnnotation.fullyQualifiedName >> Override.name
        injectAnnotation.fullyQualifiedName >> Inject.name

        repository = BinaryCompatibilityRepository.openRepositoryFor([new File(tmp.absolutePath)], [])
    }

    def cleanup() {
        repository?.close()
    }

    def "each new #apiElement requires a @Incubating annotation"() {
        given:
        JApiCompatibility jApiType = getProperty(jApiTypeName)
        def rule = withContext(new IncubatingMissingRule([:]))
        def annotations = []
        jApiType.annotations >> annotations

        when:
        annotations.clear()

        then:
        rule.maybeViolation(jApiType).humanExplanation =~ 'Is not annotated with @Incubating'

        when:
        annotations.add(incubatingAnnotation)

        then:
        rule.maybeViolation(jApiType) == null

        where:
        apiElement    | jApiTypeName
        'interface'   | 'jApiClassifier'
        'method'      | 'jApiMethod'
        'field'       | 'jApiField'
        'constructor' | 'jApiConstructor'
    }

    def "if a type is annotated with @Incubating a new #apiElement does not require it"() {
        given:
        JApiCompatibility jApiType = getProperty(jApiTypeName)

        when:
        this.jApiClassifier.annotations >> [incubatingAnnotation]

        def rule = withContext(new IncubatingMissingRule([:]))

        then:
        rule.maybeViolation(jApiType) == null

        where:
        apiElement    | jApiTypeName
        'method'      | 'jApiMethod'
        'field'       | 'jApiField'
        'constructor' | 'jApiConstructor'
    }

    def "each new #apiElement requires a @since annotation"() {
        given:
        JApiCompatibility jApiType = getProperty(jApiTypeName)
        def rule = withContext(new SinceAnnotationMissingRule([:]))

        when:
        sourceFile.text = apiElement.startsWith('enum') ? """
                public enum $TEST_INTERFACE_SIMPLE_NAME {
                    field;
                    void method() { }
                }
            """
            : apiElement.startsWith('annotation') ? """
                public @interface $TEST_INTERFACE_SIMPLE_NAME {
                    String method();
                }
            """
            : apiElement == 'interface' ? """
                public interface $TEST_INTERFACE_SIMPLE_NAME {
                    String field = "value";
                    void method();
                }
            """
            : """
                public class $TEST_INTERFACE_SIMPLE_NAME {
                    public String field = "value";
                    public void method() { }
                    public $TEST_INTERFACE_SIMPLE_NAME() { }
                }
            """

        then:
        rule.maybeViolation(jApiType).humanExplanation =~ 'Is not annotated with @since 11.38'

        when:
        repository.emptyCaches()
        sourceFile.text = apiElement == 'enum' ? """
                /**
                 * @since 11.38
                 */
                public enum $TEST_INTERFACE_SIMPLE_NAME {
                    field;

                    void method() { }
                }
            """
            : apiElement.startsWith('enum') ? """
                public enum $TEST_INTERFACE_SIMPLE_NAME {
                    /**
                     * @since 11.38
                     */
                    field;

                    /**
                     * @since 11.38
                     */
                    void method() { }
                }
            """
            : apiElement == 'annotation' ? """
                /**
                 * @since 11.38
                 */
                public @interface $TEST_INTERFACE_SIMPLE_NAME {
                    String method();
                }
            """
            : apiElement == 'annotation member' ? """
                public @interface $TEST_INTERFACE_SIMPLE_NAME {
                    /**
                     * @since 11.38
                     */
                     String method();
                }
            """
            : apiElement == 'class' ? """
                /**
                 * @since 11.38
                 */
                public class $TEST_INTERFACE_SIMPLE_NAME {
                    public String field = "value";
                    public void method() { }
                    public $TEST_INTERFACE_SIMPLE_NAME() { }
                }
            """
            : apiElement == 'interface' ? """
                /**
                 * @since 11.38
                 */
                public interface $TEST_INTERFACE_SIMPLE_NAME {
                    String field = "value";
                    void method();
                }
            """
            : """
                public class $TEST_INTERFACE_SIMPLE_NAME {
                    /**
                     * @since 11.38
                     */
                    public $TEST_INTERFACE_SIMPLE_NAME() { }

                    /**
                     * @since 11.38
                     */
                    String field = "value";

                    /**
                     * @since 11.38
                     */
                    void method();
                }
            """

        then:
        rule.maybeViolation(jApiType) == null

        where:
        apiElement          | jApiTypeName
        'interface'         | 'jApiClassifier'
        'class'             | 'jApiClassifier'
        'method'            | 'jApiMethod'
        'field'             | 'jApiField'
        'constructor'       | 'jApiConstructor'
        'enum'              | 'jApiClassifier'
        'enum literal'      | 'jApiField'
        'enum method'       | 'jApiMethod'
        'annotation'        | 'jApiClassifier'
        'annotation member' | 'jApiMethod'
    }

    def "if a type is annotated with @since a new #apiElement still requires it"() {
        given:
        JApiCompatibility jApiType = getProperty(jApiTypeName)

        when:
        sourceFile.text = apiElement.startsWith('enum') ? """
                /**
                 * @since 11.38
                 */
                public enum $TEST_INTERFACE_SIMPLE_NAME {
                    field;
                    void method() { }
                }
            """
            : apiElement == 'constructor' ? """
                /**
                 * @since 11.38
                 */
                public class $TEST_INTERFACE_SIMPLE_NAME {
                    public ApiTest() { }
                }
            """
            : apiElement == 'annotation member' ? """
                /**
                 * @since 11.38
                 */
                public @interface $TEST_INTERFACE_SIMPLE_NAME {
                     String method();
                }
            """
            : """
                /**
                 * @since 11.38
                 */
                public interface $TEST_INTERFACE_SIMPLE_NAME {
                    String field = "value";
                    void method();
                }
            """

        def rule = withContext(new SinceAnnotationMissingRule([:]))

        then:
        def violation = rule.maybeViolation(jApiType)
        violation.severity == Severity.error
        violation.humanExplanation =~ 'Is not annotated with @since 11.38'

        where:
        apiElement          | jApiTypeName
        'method'            | 'jApiMethod'
        'field'             | 'jApiField'
        'constructor'       | 'jApiConstructor'
        'enum literal'      | 'jApiField'
        'enum method'       | 'jApiMethod'
        'annotation member' | 'jApiMethod'
    }

    def "if a new #apiElement is annotated with @Deprecated it does require @Incubating or @since annotations"() {
        given:
        JApiCompatibility jApiType = getProperty(jApiTypeName)
        def incubatingMissingRule = withContext(new IncubatingMissingRule([:]))
        def sinceMissingRule = withContext(new SinceAnnotationMissingRule([:]))

        when:
        jApiType.annotations >> [deprecatedAnnotation]
        sourceFile.text = """
            @Deprecated
            public interface $TEST_INTERFACE_SIMPLE_NAME {
                @Deprecated
                String field = "value";
                @Deprecated
                void method();
            }
        """

        then:
        incubatingMissingRule.maybeViolation(jApiType).severity == Severity.error
        sinceMissingRule.maybeViolation(jApiType).severity == Severity.error

        where:
        apiElement  | jApiTypeName
        'interface' | 'jApiClassifier'
        'method'    | 'jApiMethod'
        'field'     | 'jApiField'
    }

    def "if a new method is annotated with @Override it does not require @Incubating or @since annotations"() {
        given:
        JApiCompatibility jApiType = jApiMethod
        def incubatingMissingRule = withContext(new IncubatingMissingRule([:]))
        def sinceMissingRule = withContext(new SinceAnnotationMissingRule([:]))

        when:
        sourceFile.text = """
            public class $TEST_INTERFACE_SIMPLE_NAME {
                @Override
                void method() { }
            }
        """

        then:
        incubatingMissingRule.maybeViolation(jApiType) == null
        sinceMissingRule.maybeViolation(jApiType) == null

        where:
        apiElement | jApiTypeName
        'method'   | 'jApiMethod'
        'field'    | 'jApiField'
    }

    def "new incubating API does not fail the check but is reported"() {
        given:
        def rule = withContext(new NewIncubatingAPIRule([:]))

        when:
        jApiMethod.annotations >> [incubatingAnnotation]
        def violation = rule.maybeViolation(jApiMethod)

        then:
        violation.severity == Severity.info
        violation.humanExplanation == 'New public API in 11.38 (@Incubating)'
    }

    def "constructors with @Inject annotation are not considered public API"() {
        given:
        def rule = withContext(ruleElem)
        def annotations = []
        jApiConstructor.annotations >> annotations

        when:
        annotations.clear()

        then:
        rule.maybeViolation(jApiConstructor).humanExplanation =~ error

        when:
        annotations.add(injectAnnotation)

        then:
        rule.maybeViolation(jApiConstructor) == null

        where:
        ruleElem << [new BinaryBreakingChangesRule([:]), new SinceAnnotationMissingRule([:]), new IncubatingMissingRule([:])]
        error << ['Is not binary compatible.', 'Is not annotated with @since', 'Is not annotated with @Incubating']
    }

    def "the @since annotation on inner classes is recognised"() {
        given:
        def rule = withContext(new SinceAnnotationMissingRule([:]))
        def jApiInnerClass = Stub(JApiClass)
        jApiInnerClass.fullyQualifiedName >> "$TEST_INTERFACE_NAME\$Inner"

        when:
        sourceFile.text = """
            /**
             * @since 11.38
             */
            public interface $TEST_INTERFACE_SIMPLE_NAME {
                /**
                 * @since 11.38
                 */
                public interface Inner {
                }
            }
        """

        then:
        rule.maybeViolation(jApiInnerClass) == null
    }

    def "the @since annotation on implicit enum method '#implicitMethod#paramTypes' is not required"() {
        given:
        def rule = withContext(new SinceAnnotationMissingRule([:]))
        def jApiMethod = Stub(JApiMethod)
        jApiMethod.name >> implicitMethod
        jApiMethod.jApiClass >> jApiClassifier
        jApiMethod.parameters >> paramTypes.collect { paramStub(it) }

        when:
        sourceFile.text = """
            public enum $TEST_INTERFACE_SIMPLE_NAME {
            }
        """

        then:
        rule.maybeViolation(jApiMethod) == null

        where:
        implicitMethod | paramTypes
        "values"       | []
        "valueOf"      | ["java.lang.String"]
    }

    def "the @since annotation on implicit enum method '#implicitMethod' overload is required"() {
        given:
        def rule = withContext(new SinceAnnotationMissingRule([:]))
        def jApiMethod = Stub(JApiMethod)
        jApiMethod.name >> implicitMethod
        jApiMethod.jApiClass >> jApiClassifier
        jApiMethod.parameters >> [paramStub("boolean")]

        when:
        sourceFile.text = """
            public enum $TEST_INTERFACE_SIMPLE_NAME {
            }
        """

        then:
        rule.maybeViolation(jApiMethod).humanExplanation =~ 'Is not annotated with @since 11.38'

        where:
        implicitMethod << ["values", "valueOf"]
    }

    private def paramStub(String type) {
        def stub = Stub(JApiParameter)
        stub.type >> type
        return stub
    }

    AbstractContextAwareViolationRule withContext(AbstractContextAwareViolationRule rule) {
        rule.context = new ViolationCheckContext() {

            @Override
            String getClassName() { TEST_INTERFACE_NAME }

            @Override
            Map<String, ?> getUserData() {
                [
                    currentVersion: '11.38',
                    currentMasterVersion: '11.37',
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
