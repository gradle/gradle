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

package org.gradle.api.provider

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.ExpectedDeprecationWarning
import spock.lang.Issue

/**
 * A type compiled against an older Gradle keeps declaring the eager accessor of a property that
 * has since been upgraded with {@code @ReplacesEagerProperty}, so the decorated type ends up with
 * both a concrete eager getter and an abstract lazy getter for the same property.
 *
 * That shape cannot be produced by a single compilation of a subclass — both javac and groovyc
 * reject an override that only differs in return type. It is reproduced here by inheriting the two
 * getters from different branches of the hierarchy, which groovyc does accept and which leaves the
 * class generator in the same state.
 */
@Issue("https://github.com/gradle/gradle/issues/25421")
class UpgradedPropertyLegacyGetterIntegrationTest extends AbstractIntegrationSpec {

    /**
     * These types cannot delegate to super — LegacyBase extends DefaultTask, which has no such accessor —
     * so every legacy getter here is a non-delegating body and is expected to be reported. The
     * delegating-body case, which must stay silent, is covered by FreefairAspectJPluginSmokeTest.
     */
    private static final String OVERRIDE_DEPRECATION =
        "Overriding LegacyBase.getProp() on a property upgraded to the Provider API has been deprecated. " +
            "This will fail with an error in Gradle 11. " +
            "Gradle reads 'prop' through the upgraded accessor, so this override is no longer called. " +
            "Remove the override and configure the 'prop' property instead."

    def "can generate a task type that has both an upgraded #lazyType getter and a legacy #eagerType getter"() {
        given:
        executer.expectDeprecationWarning(ExpectedDeprecationWarning.withMessage(OVERRIDE_DEPRECATION))
        buildFile """
            import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty

            interface UpgradedProp {
                @ReplacesEagerProperty
                @Internal
                $lazyType getProp()
            }

            abstract class LegacyBase extends DefaultTask {
                $eagerType getProp() { return $eagerValue }
            }

            abstract class SomeTask extends LegacyBase implements UpgradedProp {
                @TaskAction
                void go() {
                    println("task ran")
                }
            }

            tasks.register("thing", SomeTask)
        """

        expect:
        succeeds("thing")
        outputContains("task ran")

        where:
        lazyType                     | eagerType        | eagerValue
        "Property<String>"           | "String"         | '"legacy"'
        "Property<Integer>"          | "int"            | "42"
        "ListProperty<String>"       | "List<String>"   | "[]"
        "ConfigurableFileCollection" | "FileCollection" | "null"
    }

    def "the legacy primitive getter reads through the upgraded property, defaulting like the eager adapters"() {
        given:
        executer.expectDeprecationWarning(ExpectedDeprecationWarning.withMessage(OVERRIDE_DEPRECATION))
        buildFile """
            import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty

            interface UpgradedProp {
                @ReplacesEagerProperty
                @Internal
                Property<Integer> getProp()
            }

            abstract class LegacyBase extends DefaultTask {
                int getProp() { return -1 }
            }

            abstract class SomeTask extends LegacyBase implements UpgradedProp {
                private accessor(Class<?> returnType) {
                    getClass().methods.find { it.name == "getProp" && it.returnType == returnType }
                }

                @TaskAction
                void go() {
                    println("unset: " + accessor(Integer.TYPE).invoke(this))
                    accessor(Property).invoke(this).set(7)
                    println("set: " + accessor(Integer.TYPE).invoke(this))
                }
            }

            tasks.register("thing", SomeTask)
        """

        expect:
        succeeds("thing")
        // getOrElse(0), matching what the generated eager adapters return for an unset property
        outputContains("unset: 0")
        outputContains("set: 7")
    }

    def "the legacy getter reads through the upgraded property instead of its own body"() {
        given:
        executer.expectDeprecationWarning(ExpectedDeprecationWarning.withMessage(OVERRIDE_DEPRECATION))
        // Groovy cannot dispatch between two same-named getters, so each is selected by return type.
        buildFile """
            import org.gradle.internal.instrumentation.api.annotations.ReplacesEagerProperty

            interface UpgradedProp {
                @ReplacesEagerProperty
                @Internal
                Property<String> getProp()
            }

            abstract class LegacyBase extends DefaultTask {
                String getProp() { return "from legacy body" }
            }

            abstract class SomeTask extends LegacyBase implements UpgradedProp {
                private accessor(Class<?> returnType) {
                    getClass().methods.find { it.name == "getProp" && it.returnType == returnType }
                }

                @TaskAction
                void go() {
                    accessor(Property).invoke(this).set("from lazy property")
                    println("eager getter returned: " + accessor(String).invoke(this))
                }
            }

            tasks.register("thing", SomeTask)
        """

        expect:
        succeeds("thing")
        outputContains("eager getter returned: from lazy property")
    }
}
