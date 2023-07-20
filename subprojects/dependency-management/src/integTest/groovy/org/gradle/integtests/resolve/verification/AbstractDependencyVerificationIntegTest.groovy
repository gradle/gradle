/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.integtests.resolve.verification

import groovy.transform.Canonical
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.verification.report.DependencyVerificationReportWriter
import org.gradle.api.internal.artifacts.verification.DependencyVerificationFixture
import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.test.fixtures.plugin.PluginBuilder
import org.gradle.test.fixtures.server.http.MavenHttpModule
import org.gradle.test.fixtures.server.http.MavenHttpPluginRepository
import org.junit.Rule

abstract class AbstractDependencyVerificationIntegTest extends AbstractHttpDependencyResolutionTest {
    @Delegate
    private final DependencyVerificationFixture verificationFile = new DependencyVerificationFixture(
        file("gradle/verification-metadata.xml")
    )

    @Rule
    MavenHttpPluginRepository pluginRepo = MavenHttpPluginRepository.asGradlePluginPortal(executer, mavenRepo)

    def setup() {
        settingsFile << """
            rootProject.name = "dependency-verification"
        """
        println("Test running in ${testDirectory}")
    }

    protected GradleExecuter writeVerificationMetadata(String checksums = "sha1,sha512") {
        executer.withArguments("--write-verification-metadata", checksums)
    }

    protected void javaLibrary(File f = buildFile) {
        f << """
            plugins {
                id 'java-library'
            }

            repositories {
                maven {
                    url "${mavenHttpRepo.uri}"
                }
            }
        """
        def projectName = "main"
        if (f != buildFile) {
            projectName = f.parentFile.name
            settingsFile << """
                include "$projectName"
            """
        }
        file("${projectName == 'main' ? '' : "$projectName/"}src/main/java/com/acme/$projectName/Foo.java") << """
        package com.acme.$projectName;
        public class Foo {}
        """

    }

    protected MavenHttpModule uncheckedModule(String group, String name, String version = "1.0", @DelegatesTo(value = MavenHttpModule, strategy = Closure.DELEGATE_FIRST) Closure conf = null) {
        def mod = mavenHttpRepo.module(group, name, version)
            .allowAll()
        if (conf) {
            conf.resolveStrategy = Closure.DELEGATE_FIRST
            conf.delegate = mod
            conf()
        }
        mod.publish()
    }

    protected void addPlugin() {
        def pluginBuilder = new PluginBuilder(file("some-plugin"))
        pluginBuilder.addPlugin("println 'Hello, Gradle!'")
        pluginBuilder.publishAs("com", "myplugin", "1.0", pluginRepo, executer).allowAll()
    }

    static String getDocsUrl() {
        new DocumentationRegistry().getDocumentationRecommendationFor("on how to do this", "dependency_verification", "sec:troubleshooting-verification")
    }

    protected void terseConsoleOutput(boolean terse, String gradlePropertiesDir = ".") {
        if (!terse) {
            file("$gradlePropertiesDir/gradle.properties") << """${DependencyVerificationReportWriter.VERBOSE_CONSOLE}=${DependencyVerificationReportWriter.VERBOSE_VALUE}
"""
        }
    }

    protected void assertConfigCacheDiscarded() {
        if (GradleContextualExecuter.isConfigCache()) {
            failure.assertOutputContains("Configuration cache entry discarded")
        }
    }

    protected void assertVerificationError(boolean terse, @DelegatesTo(value = VerificationErrorHelper, strategy = Closure.DELEGATE_FIRST) Closure<?> verification) {
        def helper = new VerificationErrorHelper(terse)
        verification.delegate = helper
        verification.resolveStrategy = Closure.DELEGATE_FIRST
        verification()
    }

    @Canonical
    protected class VerificationErrorHelper {
        final boolean terse

        void whenTerse(String whenTerse) {
            if (terse) {
                failure.assertHasCause(whenTerse)
            }
        }

        void whenVerbose(String whenVerbose) {
            if (!terse) {
                failure.assertHasCause(whenVerbose)
            }
        }
    }
}
