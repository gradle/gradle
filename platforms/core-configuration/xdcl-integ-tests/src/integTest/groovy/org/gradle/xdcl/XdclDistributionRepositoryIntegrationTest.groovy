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

package org.gradle.xdcl

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.plugin.PluginBuilder

/**
 * The Maven repository EMBEDDED in the distribution image ({@code <gradleHome>/repo}): it serves
 * the published XDCL ecosystem libraries at the running distribution's exact (timestamped) version,
 * with real POM + Gradle Module Metadata, plus the {@code org.xdcl:xdcl-gradle-api} module their
 * published metadata STRICTLY requires — the complete closure, so a consumer build's settings
 * classpath can resolve a built-in ecosystem library offline, with no external repository in play.
 * This is the packaging half of resolving built-in ecosystems through ordinary dependency
 * resolution; the provider-side injection is exercised separately.
 */
class XdclDistributionRepositoryIntegrationTest extends AbstractIntegrationSpec {

    /**
     * A version that no artifact anywhere carries — what a published plugin's POM would
     * transitively request after being built against some other (stale) distribution. Its only
     * property of interest is being different from the running distribution's version; the pin
     * rule must rewrite it without ever trying to resolve it.
     */
    private static final String STALE_REQUESTED_VERSION = "0.0.0-stale-test-fixture"

    /**
     * A version guaranteed to order HIGHER than any real distribution version. This is the
     * direction where the pin genuinely fights Gradle: default conflict resolution picks the
     * highest version, so a newer published offer would win over the distribution's — only the
     * pin's rewrite makes "the running distribution wins" hold as a downgrade.
     */
    private static final String NEWER_OFFERED_VERSION = "9999.0.0-newer-test-fixture"

    def "a consumer build resolves an ecosystem library and its full closure from the embedded repository, offline"() {
        given: 'a build whose only repository is the distribution-embedded one'
        buildFile << '''
            def distributionRepository = new File(gradle.gradleHomeDir, "repo")
            def distributionVersion = org.gradle.util.GradleVersion.current().version

            configurations {
                probe
            }
            repositories {
                maven { url = distributionRepository.toURI() }
            }
            dependencies {
                probe "org.gradle:gradle-xdcl-plugin-development:${distributionVersion}"
            }

            tasks.register("resolveProbe") {
                def probe = configurations.probe
                doLast {
                    probe.files.name.sort().each { println("xdcl-repo-resolved=" + it) }
                }
            }
        '''

        when:
        executer.withArgument("--offline")
        succeeds("resolveProbe")

        then: 'the requested library, its ecosystem dependency, and the strictly-pinned org.xdcl API all resolve'
        outputContains("xdcl-repo-resolved=gradle-xdcl-plugin-development-")
        outputContains("xdcl-repo-resolved=gradle-xdcl-common-ecosystem-")
        // Version-agnostic on purpose: WHICH org.xdcl version the closure needs is the published
        // metadata's strict constraint, served by the same repo — not this test's business.
        outputContains("xdcl-repo-resolved=xdcl-gradle-api-")
    }

    def "the embedded repository pins the running distribution's version against a stale published request"() {
        given: "a consumer requesting a stale version, the way a published plugin's POM would"
        buildFile << """
            def distributionRepository = new File(gradle.gradleHomeDir, "repo")
            def distributionVersion = org.gradle.util.GradleVersion.current().version

            configurations {
                probe {
                    resolutionStrategy.eachDependency { details ->
                        if (details.requested.group == "org.gradle" && details.requested.name.startsWith("gradle-xdcl-")) {
                            details.useVersion(distributionVersion)
                            details.because("built-in XDCL ecosystem libraries are pinned to the running Gradle distribution")
                        }
                    }
                }
            }
            repositories {
                maven { url = distributionRepository.toURI() }
            }
            dependencies {
                probe "org.gradle:gradle-xdcl-plugin-development:${STALE_REQUESTED_VERSION}"
            }

            tasks.register("resolveProbe") {
                def result = configurations.probe.incoming.resolutionResult.rootComponent
                doLast {
                    def selected = result.get().dependencies*.selected.find { it.moduleVersion.name == "gradle-xdcl-plugin-development" }
                    println("xdcl-repo-selected-version=" + selected.moduleVersion.version)
                    println("xdcl-repo-selection-reason=" + selected.selectionReason.descriptions*.description.join("; "))
                }
            }
        """

        when:
        executer.withArgument("--offline")
        succeeds("resolveProbe")

        then: 'the stale request resolves to the distribution version, with first-class provenance'
        output.contains("xdcl-repo-selected-version=" + distribution.version.version)
        // The reason list keeps the original "requested" description ahead of the rule's.
        outputContains("built-in XDCL ecosystem libraries are pinned to the running Gradle distribution")
    }

    def "the pin downgrades a NEWER offered version to the distribution's, against default conflict resolution"() {
        given: 'a repository actually serving a newer version of the ecosystem library'
        mavenRepo.module("org.gradle", "gradle-xdcl-plugin-development", NEWER_OFFERED_VERSION).publish()

        and: 'two identical probes over both repositories — one with the pin, one control without'
        buildFile << """
            def distributionRepository = new File(gradle.gradleHomeDir, "repo")
            def distributionVersion = org.gradle.util.GradleVersion.current().version

            configurations {
                control
                pinned {
                    resolutionStrategy.eachDependency { details ->
                        if (details.requested.group == "org.gradle" && details.requested.name.startsWith("gradle-xdcl-")) {
                            details.useVersion(distributionVersion)
                            details.because("built-in XDCL ecosystem libraries are pinned to the running Gradle distribution")
                        }
                    }
                }
            }
            repositories {
                maven { url = distributionRepository.toURI() }
                maven { url = "${mavenRepo.uri}" }
            }
            dependencies {
                // Both versions requested, the way the injected companion dependency (distribution
                // version) meets a published plugin's transitive edge (newer version).
                control "org.gradle:gradle-xdcl-plugin-development:\${distributionVersion}"
                control "org.gradle:gradle-xdcl-plugin-development:${NEWER_OFFERED_VERSION}"
                pinned "org.gradle:gradle-xdcl-plugin-development:\${distributionVersion}"
                pinned "org.gradle:gradle-xdcl-plugin-development:${NEWER_OFFERED_VERSION}"
            }

            tasks.register("resolveProbe") {
                def control = configurations.control.incoming.resolutionResult.rootComponent
                def pinned = configurations.pinned.incoming.resolutionResult.rootComponent
                doLast {
                    def selectedOf = { root ->
                        root.dependencies*.selected.find { it.moduleVersion.name == "gradle-xdcl-plugin-development" }.moduleVersion.version
                    }
                    println("xdcl-repo-control-selected=" + selectedOf(control.get()))
                    println("xdcl-repo-pinned-selected=" + selectedOf(pinned.get()))
                }
            }
        """

        when:
        executer.withArgument("--offline")
        succeeds("resolveProbe")

        then: 'without the pin, default conflict resolution picks the newer offer — the pin is load-bearing'
        outputContains("xdcl-repo-control-selected=" + NEWER_OFFERED_VERSION)

        and: 'with the pin, the distribution version wins the conflict as a forced downgrade'
        output.contains("xdcl-repo-pinned-selected=" + distribution.version.version)
    }

    def "a settings plugin offering a newer ecosystem version still gets the distribution's, end to end"() {
        given: 'a declarative settings applying the built-in ecosystem AND an included-build plugin that drags in a newer library version'
        file('settings.gradle.xdcl') << '''
            settings {
              pluginManagement {
                includedBuilds ["build-logic"]
              }
              plugins [
                { id "plugin-development-ecosystem" },
                { id "newer-dep-plugin" }
              ]
              rootProject { name "probe" }
            }
        '''
        file('build-logic/settings.gradle') << 'rootProject.name = "build-logic"'
        file('build-logic/build.gradle') << """
            plugins {
                id "java-gradle-plugin"
            }
            gradlePlugin {
                plugins {
                    newerDepPlugin {
                        id = "newer-dep-plugin"
                        implementationClass = "my.NewerDepPlugin"
                    }
                }
            }
            dependencies {
                // runtimeOnly: rides the plugin's published runtime metadata onto the consuming
                // build's settings classpath (the shape of a plugin compiled against a newer
                // distribution) without this build having to resolve it itself.
                runtimeOnly "org.gradle:gradle-xdcl-plugin-development:${NEWER_OFFERED_VERSION}"
            }
        """
        file('build-logic/src/main/java/my/NewerDepPlugin.java') << '''
            package my;

            import org.gradle.api.Plugin;
            import org.gradle.api.initialization.Settings;

            public class NewerDepPlugin implements Plugin<Settings> {
                @Override public void apply(Settings target) {
                }
            }
        '''
        buildFile << '''
            def classpath = gradle.settings.buildscript.configurations.getByName("classpath")
            def selected = classpath.incoming.resolutionResult.allComponents.find {
                it.moduleVersion?.name == "gradle-xdcl-plugin-development"
            }
            println("xdcl-e2e-selected=" + selected.moduleVersion.version)
            println("xdcl-e2e-reason=" + selected.selectionReason.descriptions*.description.join("; "))
        '''

        when:
        succeeds("help")

        then: 'the newer offer never resolves — the provider pin downgrades the whole conflict to the distribution version'
        output.contains("xdcl-e2e-selected=" + distribution.version.version)
        outputContains("built-in XDCL ecosystem libraries are pinned to the running Gradle distribution")
    }

    def "a repository-published plugin's transitive dependency on a newer ecosystem library is downgraded to the distribution's"() {
        given: "a third-party ecosystem plugin PUBLISHED to a repository, its POM carrying a transitive dependency on a NEWER built-in library version — the shape of an ecosystem extension compiled against a newer distribution (the newer version itself exists nowhere: the pin must rewrite the request before anything tries to resolve it)"
        def pluginBuilder = new PluginBuilder(file("third-party-ecosystem"))
        pluginBuilder.addSettingsPlugin(
            'println "third-party-ecosystem applied"',
            "third-party-ecosystem",
            "ThirdPartyEcosystemPlugin"
        )
        def pluginModule = mavenRepo.module("com.example", "third-party-ecosystem-plugin", "1.0")
        pluginModule.dependsOn("org.gradle", "gradle-xdcl-plugin-development", NEWER_OFFERED_VERSION)
        pluginModule.publish()
        // The same jar + marker layout PluginBuilder.publishAs produces, inlined so the
        // implementation module's POM can carry the transitive dependency above.
        def marker = mavenRepo.module("third-party-ecosystem", "third-party-ecosystem" + PluginBuilder.PLUGIN_MARKER_SUFFIX, "1.0")
        marker.dependsOn(pluginModule)
        marker.publish()
        pluginBuilder.publishTo(executer, pluginModule.artifactFile)

        and: 'a declarative settings resolving that plugin from the repository and applying it beside the built-in ecosystem'
        file('settings.gradle.xdcl') << """
            settings {
              pluginManagement {
                repositories ["${mavenRepo.uri}"]
              }
              plugins [
                { id "plugin-development-ecosystem" },
                { id "third-party-ecosystem" version "1.0" }
              ]
              rootProject { name "probe" }
            }
        """
        buildFile << '''
            def classpath = gradle.settings.buildscript.configurations.getByName("classpath")
            def selected = classpath.incoming.resolutionResult.allComponents.find {
                it.moduleVersion?.name == "gradle-xdcl-plugin-development"
            }
            println("xdcl-transitive-selected=" + selected.moduleVersion.version)
            println("xdcl-transitive-reason=" + selected.selectionReason.descriptions*.description.join("; "))
        '''

        when:
        succeeds("help")

        then: 'the plugin itself resolved from the repository and applied'
        outputContains("third-party-ecosystem applied")

        and: "its POM's transitive request never resolves at the newer version — the pin downgrades it to the running distribution's"
        output.contains("xdcl-transitive-selected=" + distribution.version.version)
        outputContains("built-in XDCL ecosystem libraries are pinned to the running Gradle distribution")
    }
}
