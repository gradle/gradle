/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.integtests.tooling.fixture

import org.gradle.tooling.model.DomainObjectSet
import org.gradle.tooling.model.internal.ImmutableDomainObjectSet
import spock.lang.Specification

class ToolingApiModelCheckerTest extends Specification {

    def "passes with equal values"() {
        when:
        ToolingApiModelChecker.checkModel(
            dummyModel("someValue"),
            dummyModel("some" + "Value"),
            [
                { it.value }
            ]
        )

        then:
        noExceptionThrown()
    }

    def "passes with equal values from different model implementations"() {
        DummyModel actual = new DummyModel() {
            @Override
            String getValue() {
                return "someValue"
            }
        }

        when:
        ToolingApiModelChecker.checkModel(
            actual,
            dummyModel("someValue"),
            [
                { it.value }
            ]
        )

        then:
        noExceptionThrown()
    }

    def "fails with not equal values"() {
        when:
        ToolingApiModelChecker.checkModel(
            dummyModel("someValue"),
            dummyModel("otherValue"),
            [
                { it.value }
            ]
        )

        then:
        def error = thrown(AssertionError)
        error.message.contains("assert getter(actual) == getter(expected)")
        error.message.contains("       |      |       |  |")
        error.message.contains("       |      |       |  'otherValue'")
        error.message.contains("       'someValue'")
    }

    def "fails with not equal values with sub specs"() {
        when:
        ToolingApiModelChecker.checkModel(
            dummyCompositeModel(dummyModel("one")),
            dummyCompositeModel(dummyModel("notOne")),
            [
                [{ it.modelOne }, [{ it.value }]]
            ]
        )

        then:
        true
        def error = thrown(AssertionError)
        error.message.contains("assert getter(actual) == getter(expected)")
        error.message.contains("       |      |       |  |")
        error.message.contains("       'one'  |       |  |")
        error.message.contains("              |       |  'notOne'")
    }

    def "fails with not equal values in sub-model check"() {
        when:
        ToolingApiModelChecker.checkModel(
            dummyCompositeModel(dummyModel("one")),
            dummyCompositeModel(dummyModel("notOne")),
            [
                [{ it.modelOne }, { a, e ->
                    ToolingApiModelChecker.checkModel(a, e, [{ it.value }])
                }]
            ]
        )

        then:
        def error = thrown(AssertionError)
        error.message.contains("assert getter(actual) == getter(expected)")
        error.message.contains("       |      |       |  |")
        error.message.contains("       'one'  |       |  |")
        error.message.contains("              |       |  'notOne'")
    }

    def "fails with not equal domain set sizes"() {
        when:
        ToolingApiModelChecker.checkModel(
            dummyDomainSetModel(dummyModel("one")),
            dummyDomainSetModel(dummyModel("one"), dummyModel("two")),
            [
                [{ it.values }, { a, e ->
                    throw new RuntimeException("Never called")
                }]
            ]
        )

        then:
        def error = thrown(AssertionError)
        error.message.contains("assert actual.size() == expected.size()")
        error.message.contains("       |      |      |  |        |")
        error.message.contains("       |      1      |  |        2")
    }

    def "fails with not equal domain set items"() {
        when:
        ToolingApiModelChecker.checkModel(
            dummyDomainSetModel(dummyModel("one"), dummyModel("two")),
            dummyDomainSetModel(dummyModel("one"), dummyModel("three")),
            [
                [{ it.values }, { a, e ->
                    ToolingApiModelChecker.checkModel(a, e, [{ it.value }])
                }]
            ]
        )

        then:
        def error = thrown(AssertionError)
        error.message.contains("assert getter(actual) == getter(expected)")
        error.message.contains("       |      |       |  |")
        error.message.contains("       'two'  |       |  |")
        error.message.contains("              |       |  'three'")
    }

    private DummyModel dummyModel(String value) {
        return new DummyModel() {
            @Override
            String getValue() {
                return value
            }
        }
    }

    private DummyCompositeModel dummyCompositeModel(DummyModel model1) {
        return new DummyCompositeModel() {
            @Override
            DummyModel getModelOne() {
                return model1
            }
        }
    }

    private DummyDomainSetModel dummyDomainSetModel(DummyModel... values) {
        return dummyDomainSetModel(values.toList())
    }

    private DummyDomainSetModel dummyDomainSetModel(Collection<? extends DummyModel> values) {
        def domainObjectSet = ImmutableDomainObjectSet.of(values)
        return new DummyDomainSetModel() {
            @Override
            DomainObjectSet<? extends DummyModel> getValues() {
                return domainObjectSet
            }
        }
    }

    interface DummyModel {
        String getValue()
    }

    interface DummyCompositeModel {
        DummyModel getModelOne()
    }

    interface DummyDomainSetModel {
        DomainObjectSet<? extends DummyModel> getValues()
    }

}
