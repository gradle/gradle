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
import org.gradle.integtests.fixtures.configurationcache.ConfigurationCacheFixture
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.TestExecutionPreconditions
import spock.lang.Issue

class ValueSourceParametersIntegrationTest extends AbstractIntegrationSpec {

    @Issue("https://github.com/gradle/gradle/issues/39090")
    def "parameters injecting a service warn once per type and the service is usable from obtain"() {
        given:
        buildFile """
            import org.gradle.api.provider.*
            import javax.inject.Inject

            interface Params extends ValueSourceParameters {
                @Inject ObjectFactory getObjects()
            }

            abstract class Probe implements ValueSource<String, Params> {
                @Override String obtain() {
                    return "objects=" + parameters.objects.getClass().name
                }
            }

            println("first = " + providers.of(Probe) {}.get())
            println("second = " + providers.of(Probe) {}.get())
        """

        when:
        expectServiceInjectionDeprecation("Params", "org.gradle.api.model.ObjectFactory")
        run("help")

        then:
        outputContains("first = objects=org.gradle.api.internal.model.DefaultObjectFactory")
        outputContains("second = objects=org.gradle.api.internal.model.DefaultObjectFactory")
    }

    @Issue("https://github.com/gradle/gradle/issues/39090")
    def "nested managed type injecting a service warns naming the nested type"() {
        given:
        buildFile """
            import org.gradle.api.provider.*
            import javax.inject.Inject

            interface Inner {
                @Inject ObjectFactory getObjects()
            }

            interface Params extends ValueSourceParameters {
                @Nested Inner getInner()
            }

            abstract class Probe implements ValueSource<String, Params> {
                @Override String obtain() {
                    return "objects=" + parameters.inner.objects.getClass().name
                }
            }

            println("probe = " + providers.of(Probe) {}.get())
        """

        when:
        expectServiceInjectionDeprecation("Params", "Inner", "org.gradle.api.model.ObjectFactory")
        run("help")

        then:
        outputContains("probe = objects=org.gradle.api.internal.model.DefaultObjectFactory")
    }

    @Issue("https://github.com/gradle/gradle/issues/39090")
    def "each build of a composite warns for its own parameters type"() {
        given:
        settingsFile """
            rootProject.name = "root"
            includeBuild("included")
        """
        file("gradle.properties") << "probe.name = root\n"
        buildFile """
            ${probeWithInjectedProviders("root")}
            tasks.register("ok") {
                dependsOn(gradle.includedBuild("included").task(":ok"))
            }
        """
        file("included/settings.gradle") << """
            rootProject.name = "included"
        """
        file("included/gradle.properties") << "probe.name = included\n"
        file("included/build.gradle") << """
            ${probeWithInjectedProviders("included")}
            tasks.register("ok")
        """

        when:
        expectServiceInjectionDeprecation("Params", "org.gradle.api.provider.ProviderFactory")
        expectServiceInjectionDeprecation("Params", "org.gradle.api.provider.ProviderFactory")
        run("ok")

        then:
        outputContains("root name = root")
        outputContains("included name = included")
    }

    @Requires(value = TestExecutionPreconditions.NotConfigCached, reason = "controls the configuration cache explicitly")
    @Issue("https://github.com/gradle/gradle/issues/39090")
    def "configuration cache hit does not warn"() {
        given:
        def configurationCache = new ConfigurationCacheFixture(this)
        buildFile """
            import org.gradle.api.provider.*
            import javax.inject.Inject

            interface Params extends ValueSourceParameters {
                @Inject ObjectFactory getObjects()
            }

            abstract class Probe implements ValueSource<String, Params> {
                @Override String obtain() {
                    return "objects=" + parameters.objects.getClass().name
                }
            }

            println("probe = " + providers.of(Probe) {}.get())
        """

        when:
        expectServiceInjectionDeprecation("Params", "org.gradle.api.model.ObjectFactory")
        run("help", "--configuration-cache")

        then:
        configurationCache.assertStateStored()
        outputContains("probe = objects=org.gradle.api.internal.model.DefaultObjectFactory")

        when:
        run("help", "--configuration-cache")

        then:
        configurationCache.assertStateLoaded()
    }

    private static String probeWithInjectedProviders(String label) {
        """
            import org.gradle.api.provider.*
            import javax.inject.Inject

            interface Params extends ValueSourceParameters {
                @Inject ProviderFactory getProviders()
            }

            abstract class Probe implements ValueSource<String, Params> {
                @Override String obtain() {
                    return parameters.providers.gradleProperty("probe.name").getOrElse("<absent>")
                }
            }

            println("$label name = " + providers.of(Probe) {}.get())
        """
    }

    private void expectServiceInjectionDeprecation(String parametersType, String serviceType) {
        expectServiceInjectionDeprecationOf(parametersType, "'$serviceType'")
    }

    private void expectServiceInjectionDeprecation(String parametersType, String nestedType, String serviceType) {
        expectServiceInjectionDeprecationOf(parametersType, "'$serviceType' through '$nestedType'")
    }

    private void expectServiceInjectionDeprecationOf(String parametersType, String injection) {
        executer.expectDocumentedDeprecationWarning(
            "Injecting services into value source parameters has been deprecated. " +
                "This will fail with an error in Gradle 10. " +
                "Type '$parametersType' injects $injection. " +
                "Value source parameters must only hold data. " +
                "Compute the value that needs the service when creating the provider and pass it as a parameter, " +
                "or inject the service into the ValueSource implementation instead. " +
                "Consult the upgrading guide for further information: " +
                "https://docs.gradle.org/current/userguide/upgrading_version_9.html#value_source_parameters_service_injection"
        )
    }
}
