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
import org.gradle.util.internal.ToBeImplemented
import spock.lang.Issue

class ValueSourceParametersIntegrationTest extends AbstractIntegrationSpec {

    @ToBeImplemented("Services injected into value source parameters are not rejected yet")
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

            println("first = " + providers.of(Probe) {}.get())
            println("second = " + providers.of(Probe) {}.get())
        """

        when:
        run("help")

        then:
        outputContains("first = made by the injected factory")
        outputContains("second = made by the injected factory")
    }

    @ToBeImplemented("Services injected into value source parameters are not rejected yet")
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
        run("help")

        then:
        outputContains("probe = made by the nested factory")
    }

    @ToBeImplemented("Services injected into value source parameters are not rejected yet")
    @Issue("https://github.com/gradle/gradle/issues/39090")
    def "parameters cannot inject a service in each build of a composite"() {
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
        run("ok")

        then:
        outputContains("root name = root")
        outputContains("included name = included")
    }

    private String probeWithInjectedProviders(String label) {
        buildScriptSnippet """
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
}
