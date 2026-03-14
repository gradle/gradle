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

package org.gradle.features

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.polyglot.PolyglotDslTest
import org.gradle.integtests.fixtures.polyglot.PolyglotTestFixture
import org.gradle.integtests.fixtures.polyglot.SkipDsl
import org.gradle.features.internal.ProjectFeatureFixture
import org.gradle.test.fixtures.dsl.GradleDsl

@PolyglotDslTest
class ProjectFeatureMutabilityIntegrationTest extends AbstractIntegrationSpec
    implements ProjectFeatureFixture, PolyglotTestFixture {

    def setup() {
        file("gradle.properties") << "org.gradle.kotlin.dsl.dcl=true"
    }

    def "apply action can eagerly read property values and nested property values configured in the build script"() {
        given:
        def pluginBuilder = withProjectTypeThatReadsValuesEagerlyInApplyAction()
        pluginBuilder.addBuildScriptContent pluginBuildScriptForJava
        pluginBuilder.prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << """
            testProjectType {
                id = "my-app"
                foo {
                    bar = "my-nested-bar"
                }
            }
        """

        when:
        run(":printApplyTimeValues")

        then:
        outputContains("apply time id = my-app")
        outputContains("apply time foo.bar = my-nested-bar")
    }

    @SkipDsl(dsl = GradleDsl.DECLARATIVE, because = "DCL does not support post-block modification syntax")
    def "definition property cannot be modified after the project type configuration block exits"() {
        given:
        def pluginBuilder = withProjectType()
        pluginBuilder.addBuildScriptContent pluginBuildScriptForJava
        pluginBuilder.prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << """
            testProjectType {
                id = "original"
                foo {
                    bar = "baz"
                }
                project.afterEvaluate {
                    id = "attempted-late-change"
                    foo.bar = "attempted-late-nested-change"
                }
            }
        """

        when:
        fails(":help")

        then:
        failure.assertHasCause("The value for property 'id' is final and cannot be changed any further.")
    }

    @SkipDsl(dsl = GradleDsl.DECLARATIVE, because = "DCL controls NDOC elements declaratively")
    def "named domain object container cannot be modified after the project type configuration block exits"() {
        given:
        def pluginBuilder = withProjectTypeWithDefinitionThatHasNdocContainingDefinitions()
        pluginBuilder.addBuildScriptContent pluginBuildScriptForJava
        pluginBuilder.prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << """
            testProjectType {
                id = "app"
                sources {
                    register("main") {
                        sourceDir = "src/main/java"
                    }
                }
                project.afterEvaluate {
                    sources.register("late-addition")
                }
            }
        """

        when:
        fails(":help")

        then:
        failure.assertHasCause("Cannot call register(String) on Source container as changes to this collection are disallowed.")
    }

    def "apply action can access auto-registered build models for elements in a NamedDomainObjectContainer<Definition>"() {
        given:
        def pluginBuilder = withProjectTypeWithDefinitionThatHasNdocContainingDefinitions()
        pluginBuilder.addBuildScriptContent pluginBuildScriptForJava
        pluginBuilder.prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << """
            testProjectType {
                id = "my-project"
                sources {
                    ${registerNdocElement(currentDsl(), "source", "main")} { sourceDir = "src/main/java" }
                    ${registerNdocElement(currentDsl(), "source", "test")} { sourceDir = "src/test/java" }
                }
            }
        """

        when:
        run(":printSourceModels")

        then:
        outputContains("source processed dir = SRC/MAIN/JAVA")
        outputContains("source processed dir = SRC/TEST/JAVA")
    }

    def "nested build models can be instantiated with a separate implementation type"() {
        given:
        def pluginBuilder = withProjectTypeWithDefinitionThatHasNdocContainingDefinitions()
        pluginBuilder.addBuildScriptContent pluginBuildScriptForJava
        pluginBuilder.prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << """
            testProjectType {
                id = "my-project"
                sources {
                    ${registerNdocElement(currentDsl(), "source", "main")} { sourceDir = "src" }
                }
            }
        """

        when:
        run(":printSourceModelClass")

        then:
        outputContains("source model class: ProjectTypeImplPlugin\$Binding\$DefaultSourceModel_Decorated")
    }

    def "apply action can eagerly read property values from an NDOC element definition configured in the build script"() {
        given:
        def pluginBuilder = withProjectTypeWithDefinitionNdocThatReadsValuesEagerly()
        pluginBuilder.addBuildScriptContent pluginBuildScriptForJava
        pluginBuilder.prepareToExecute()

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << """
            testProjectType {
                id = "my-project"
                sources {
                    ${registerNdocElement(currentDsl(), "source", "main")} { sourceDir = "src/main/java" }
                }
            }
        """

        when:
        run(":printEagerSourceValues")

        then:
        outputContains("eager source dir for main = src/main/java")
    }

    static String registerNdocElement(GradleDsl dsl, String elementType, String name) {
        return dsl == GradleDsl.DECLARATIVE ? """${elementType}("${name}")""" : """register("${name}")"""
    }
}
