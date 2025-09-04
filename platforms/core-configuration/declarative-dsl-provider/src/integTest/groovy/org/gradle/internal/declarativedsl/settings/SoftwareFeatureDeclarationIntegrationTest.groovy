/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.declarativedsl.settings

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.polyglot.PolyglotDslTest
import org.gradle.integtests.fixtures.polyglot.PolyglotTestFixture
import org.gradle.internal.declarativedsl.DeclarativeTestUtils
import org.gradle.test.fixtures.plugin.PluginBuilder
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

@PolyglotDslTest
class SoftwareFeatureDeclarationIntegrationTest extends AbstractIntegrationSpec implements SoftwareFeatureFixture, PolyglotTestFixture {

    def setup() {
        file("gradle.properties") << "org.gradle.kotlin.dsl.dcl=true"
    }

    def 'can declare and configure a custom software feature from included build'() {
        given:
        PluginBuilder pluginBuilder = withSoftwareFeaturePlugins()
        pluginBuilder.prepareToExecute()
        pluginBuilder.buildFile << "\n" + """

            tasks.withType(JavaCompile).configureEach {
                sourceCompatibility = "1.8"
                targetCompatibility = "1.8"
            }
        """

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << declarativeScriptThatConfiguresOnlyTestSoftwareFeature << DeclarativeTestUtils.nonDeclarativeSuffixForKotlinDsl

        when:
        run(":printTestSoftwareTypeExtensionConfiguration",":printTestSoftwareFeatureConfiguration")

        then:
        assertThatDeclaredValuesAreSetProperly()

        and:
        outputContains("Applying SoftwareTypeImplPlugin")
        outputContains("Binding TestSoftwareTypeExtension")
        outputContains("Binding FeatureDefinition")
    }

    @Requires(UnitTestPreconditions.Jdk23OrEarlier) // Because Kotlin does not support 24 yet and falls back to 23 causing inconsistent JVM targets
    def "can declare and configure a custom software feature in Kotlin"() {
        PluginBuilder pluginBuilder = withKotlinSoftwareFeaturePlugins()
        pluginBuilder.applyBuildScriptPlugin("org.jetbrains.kotlin.jvm", "2.1.0")
        pluginBuilder.prepareToExecute()
        pluginBuilder.buildFile << "\n" + """
            import org.jetbrains.kotlin.gradle.dsl.JvmTarget

            repositories {
                mavenCentral()
            }

            kotlin {
                compilerOptions {
                    jvmTarget = JvmTarget.JVM_1_8
                }
            }

            tasks.withType(JavaCompile).configureEach {
                sourceCompatibility = "1.8"
                targetCompatibility = "1.8"
            }
        """

        settingsFile() << pluginsFromIncludedBuild

        buildFile() << declarativeScriptThatConfiguresOnlyTestSoftwareFeature << DeclarativeTestUtils.nonDeclarativeSuffixForKotlinDsl

        when:
        run(":printTestSoftwareTypeExtensionConfiguration",":printTestSoftwareFeatureConfiguration")

        then:
        assertThatDeclaredValuesAreSetProperly()

        and:
        outputContains("Applying SoftwareTypeImplPlugin")
        outputContains("Binding TestSoftwareTypeExtension")
        outputContains("Binding FeatureDefinition")
    }

    static String getDeclarativeScriptThatConfiguresOnlyTestSoftwareFeature() {
        return """
            testSoftwareType {
                id = "test"

                foo {
                    bar = "baz"
                }

                feature {
                    text = "foo"
                }
            }
        """
    }

    void assertThatDeclaredValuesAreSetProperly() {
        outputContains("""id = test\nbar = baz""")
        outputContains("feature text = foo")
    }
}
