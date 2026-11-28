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
import spock.lang.Issue

class ValueSourceParametersIntegrationTest extends AbstractIntegrationSpec {

    @Issue("https://github.com/gradle/gradle/issues/39090")
    def "parameters cannot inject a service"() {
        given:
        buildFile """
            import org.gradle.api.provider.*
            import javax.inject.Inject

            interface Params extends ValueSourceParameters {
                @Inject ObjectFactory getObjects()
            }

            abstract class Probe implements ValueSource<String, Params> {
                @Override String obtain() {
                    return parameters.objects.property(String).value("made by the injected factory").get()
                }
            }

            println("probe = " + providers.of(Probe) {}.get())
        """

        when:
        fails("help")

        then:
        assertServiceInjectionFailed("Probe", "Params", "'org.gradle.api.model.ObjectFactory'")
    }

    @Issue("https://github.com/gradle/gradle/issues/39090")
    def "parameters cannot inject a service through a nested managed type"() {
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
                    return parameters.inner.objects.property(String).value("made by the nested factory").get()
                }
            }

            println("probe = " + providers.of(Probe) {}.get())
        """

        when:
        fails("help")

        then:
        assertServiceInjectionFailed("Probe", "Params", "'org.gradle.api.model.ObjectFactory' through 'Inner'")
    }

    @Issue("https://github.com/gradle/gradle/issues/39090")
    def "parameters cannot inject a service in an included build"() {
        given:
        settingsFile """
            rootProject.name = "root"
            includeBuild("included")
        """
        buildFile """
            tasks.register("ok") {
                dependsOn(gradle.includedBuild("included").task(":ok"))
            }
        """
        file("included/settings.gradle") << """
            rootProject.name = "included"
        """
        file("included/build.gradle") << """
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

            println("included name = " + providers.of(Probe) {}.get())
            tasks.register("ok")
        """

        when:
        fails("ok")

        then:
        failure.assertHasFileName("Build file '${file("included/build.gradle")}'")
        assertServiceInjectionFailed("Probe", "Params", "'org.gradle.api.provider.ProviderFactory'")
    }

    @Issue("https://github.com/gradle/gradle/issues/39090")
    def "parameters can reference a build service"() {
        given:
        buildFile """
            import org.gradle.api.provider.*
            import org.gradle.api.services.*

            abstract class Counter implements BuildService<BuildServiceParameters.None> {
                int count = 0
                int next() { return ++count }
            }

            interface Params extends ValueSourceParameters {
                @ServiceReference("counter") Property<Counter> getCounter()
            }

            abstract class Probe implements ValueSource<String, Params> {
                @Override String obtain() {
                    return "count = " + parameters.counter.get().next()
                }
            }

            def counter = gradle.sharedServices.registerIfAbsent("counter", Counter) {}
            tasks.register("ok") {
                usesService(counter)
                def probe = providers.of(Probe) {}
                doLast { println("probe " + probe.get()) }
            }
        """

        when:
        run("ok")

        then:
        outputContains("probe count = 1")
    }

    private void assertServiceInjectionFailed(String valueSourceType, String parametersType, String injection) {
        failure.assertHasCause(
            "Could not create provider for value source $valueSourceType. " +
                "Parameters type '$parametersType' cannot inject $injection. " +
                "Parameters must only hold data."
        )
        failure.assertHasResolution(
            "Pass the values that need the service in as parameters, " +
                "or inject a supported service into the value source implementation instead."
        )
        failure.assertHasResolution(
            documentationRegistry.getDocumentationRecommendationFor("information", "configuration_cache_requirements", "config_cache:requirements:value_source")
        )
    }
}
