/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.notations

import org.gradle.api.artifacts.Dependency
import org.gradle.api.internal.ClassPathRegistry
import org.gradle.api.internal.artifacts.dependencies.DefaultSelfResolvingDependency
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactoryInternal
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.runtimeshaded.RuntimeShadedJarFactory
import org.gradle.api.internal.runtimeshaded.RuntimeShadedJarType
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.classpath.DefaultClassPath
import org.gradle.internal.installation.CurrentGradleInstallation
import org.gradle.internal.installation.GradleInstallation
import org.gradle.internal.typeconversion.NotationParserBuilder
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactoryInternal.ClassPathNotation.GRADLE_API
import static org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactoryInternal.ClassPathNotation.GRADLE_TEST_KIT
import static org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactoryInternal.ClassPathNotation.LOCAL_GROOVY

@UsesNativeServices
class DependencyClassPathNotationConverterTest extends Specification {

    @Rule
    TestNameTestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider(getClass())

    def instantiator = TestUtil.instantiatorFactory().decorateLenient()
    def classPathRegistry = Mock(ClassPathRegistry)
    def fileCollectionFactory = TestFiles.fileCollectionFactory()
    def shadedJarFactory = Mock(RuntimeShadedJarFactory)
    def gradleInstallation = Mock(CurrentGradleInstallation)
    def factory = new DependencyClassPathNotationConverter(instantiator, classPathRegistry, fileCollectionFactory, shadedJarFactory, gradleInstallation)
    def shadedApiJar = testDirectoryProvider.file('gradle-api-shaded.jar')
    def localGroovyFiles = [testDirectoryProvider.file('groovy.jar')]
    def installationBeaconFiles = [testDirectoryProvider.file('gradle-installation.jar')]

    def setup() {
        def gradleApiFiles = [testDirectoryProvider.file('gradle-api.jar')]
        def gradleTestKitFiles = [testDirectoryProvider.file('gradle-test-kit.jar')]

        classPathRegistry.getClassPath('GRADLE_API') >> DefaultClassPath.of(gradleApiFiles)
        classPathRegistry.getClassPath('GRADLE_KOTLIN_DSL') >> ClassPath.EMPTY
        classPathRegistry.getClassPath('GRADLE_TEST_KIT') >> DefaultClassPath.of(gradleTestKitFiles)
        classPathRegistry.getClassPath('LOCAL_GROOVY') >> DefaultClassPath.of(localGroovyFiles)
        classPathRegistry.getClassPath('GRADLE_INSTALLATION_BEACON') >> DefaultClassPath.of(installationBeaconFiles)

        gradleInstallation.installation >> new GradleInstallation(testDirectoryProvider.file("gradle-home"))

        shadedJarFactory.get(RuntimeShadedJarType.API, _) >> shadedApiJar
    }

    def "parses classpath literal"() {
        when:
        def out = parse(GRADLE_API)

        then:
        out instanceof DefaultSelfResolvingDependency
        out.files as List == [shadedApiJar] + localGroovyFiles + installationBeaconFiles
    }

    def "reuses dependency instances"() {
        when:
        def out = parse(GRADLE_API)

        then:
        out instanceof DefaultSelfResolvingDependency

        when: // same instance is reused
        def out2 = parse(GRADLE_API)

        then:
        out2.is out
    }

    def "assigns component identifier to dependency"() {
        expect:
        def dep = parse(notation)
        dep.targetComponentId.displayName == displayName

        where:
        notation        | displayName
        GRADLE_API      | "Gradle API"
        GRADLE_TEST_KIT | "Gradle TestKit"
        LOCAL_GROOVY    | "Local Groovy"
    }

    def parse(def value) {
        return NotationParserBuilder.toType(Dependency).fromType(DependencyFactoryInternal.ClassPathNotation, factory).toComposite().parseNotation(value)
    }
}
