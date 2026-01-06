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

package org.gradle.api.internal.artifacts.repositories.distribution

import org.gradle.api.internal.classpath.DefaultModuleRegistry
import org.gradle.api.internal.classpath.ModuleRegistry
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.internal.installation.GradleInstallation
import spock.lang.Specification

/**
 * Tests {@link DefaultAvailableDistributionModules}.
 */
class DefaultAvailableDistributionModulesTest extends Specification {

    private final testInstallation = new GradleInstallation(IntegrationTestBuildContext.INSTANCE.gradleHomeDir)
    private final ModuleRegistry moduleRegistry = new DefaultModuleRegistry(testInstallation)

    private final DefaultAvailableDistributionModules underTest = new DefaultAvailableDistributionModules(moduleRegistry)

    def "exposes a known set of modules"() {
        given:
        def groovyVersion = GroovySystem.version

        expect:
        // This list serves as documentation/control over which dependencies the
        // `gradleDistribution()` repository allows users to resolve from the distribution.
        // If you PR changes this list, you've changed user-facing behavior.
        // * No dependency may be removed from this list without deprecation.
        // * Any dependency added to this list should be done so with careful consideration.
        underTest.getAvailableModules().collect { it.getDisplayName() } as Set == ([
            "org.apache.groovy:groovy-nio:${groovyVersion}",
            "org.apache.groovy:groovy-docgenerator:${groovyVersion}",
            "org.apache.ant:ant-launcher:1.10.15",
            "org.apache.ant:ant:1.10.15",
            "com.thoughtworks.qdox:qdox:1.12.1",
            "com.github.javaparser:javaparser-core:3.27.1",
            "org.apache.groovy:groovy-json:${groovyVersion}",
            "org.apache.groovy:groovy-groovydoc:${groovyVersion}",
            "org.apache.groovy:groovy-datetime:${groovyVersion}",
            "org.apache.groovy:groovy-xml:${groovyVersion}",
            "org.apache.groovy:groovy-astbuilder:${groovyVersion}",
            "org.apache.ant:ant-antlr:1.10.15",
            "org.apache.groovy:groovy:${groovyVersion}",
            "org.apache.groovy:groovy-bom:${groovyVersion}",
            "org.apache.groovy:groovy-templates:${groovyVersion}",
            "org.apache.groovy:groovy-dateutil:${groovyVersion}",
            "org.apache.groovy:groovy-ant:${groovyVersion}"
        ] as Set)
    }

}
