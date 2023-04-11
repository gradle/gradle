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

package org.gradle.java

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import org.gradle.test.fixtures.server.http.MavenHttpModule

class JavaLibraryPublishedTargetJvmVersionIntegrationTest extends AbstractHttpDependencyResolutionTest {
    ResolveTestFixture resolve
    MavenHttpModule module

    def setup() {
        settingsFile << """
            rootProject.name = 'test'
        """
        buildFile << """
            apply plugin: 'java-library'

            repositories {
                maven { url '${mavenHttpRepo.uri}' }
            }

            dependencies {
                api 'org:producer:1.0'
            }
        """

        resolve = new ResolveTestFixture(buildFile, 'compileClasspath')
        resolve.prepare()

        module = mavenHttpRepo.module('org', 'producer', '1.0')
                .withModuleMetadata()
                .adhocVariants()
                .variant("apiElementsJdk6", [
                'org.gradle.dependency.bundling': 'external',
                'org.gradle.jvm.version': 6,
                'org.gradle.category': 'library',
                'org.gradle.libraryelements': 'jar',
                'org.gradle.usage': 'java-api'], { artifact('producer-1.0-jdk6.jar') })
                .variant("apiElementsJdk7", [
                'org.gradle.dependency.bundling': 'external',
                'org.gradle.jvm.version': 7,
                'org.gradle.category': 'library',
                'org.gradle.libraryelements': 'jar',
                'org.gradle.usage': 'java-api'], { artifact('producer-1.0-jdk7.jar') })
                .variant("apiElementsJdk9", [
                'org.gradle.dependency.bundling': 'external',
                'org.gradle.jvm.version': 9,
                'org.gradle.category': 'library',
                'org.gradle.libraryelements': 'jar',
                'org.gradle.usage': 'java-api'], { artifact('producer-1.0-jdk9.jar') })
                .variant("runtimeElementsJdk6", [
                'org.gradle.dependency.bundling': 'external',
                'org.gradle.jvm.version': 6,
                'org.gradle.category': 'library',
                'org.gradle.libraryelements': 'jar',
                'org.gradle.usage': 'java-runtime'], { artifact('producer-1.0-jdk6.jar') })
                .variant("runtimeElementsJdk7", [
                'org.gradle.dependency.bundling': 'external',
                'org.gradle.jvm.version': 7,
                'org.gradle.category': 'library',
                'org.gradle.libraryelements': 'jar',
                'org.gradle.usage': 'java-runtime'], { artifact('producer-1.0-jdk7.jar') })
                .variant("runtimeElementsJdk9", [
                'org.gradle.dependency.bundling': 'external',
                'org.gradle.jvm.version': 9,
                'org.gradle.category': 'library',
                'org.gradle.libraryelements': 'jar',
                'org.gradle.usage': 'java-runtime'], { artifact('producer-1.0-jdk9.jar') })
                .publish()

    }

    def "can fail resolution if producer doesn't have appropriate target version"() {
        buildFile << """
            configurations.compileClasspath.attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 5)
        """

        when:
        module.pom.expectGet()
        module.moduleMetadata.expectGet()

        fails ':checkDeps'

        then:
        failure.assertHasCause('''No matching variant of org:producer:1.0 was found. The consumer was configured to find a library for use during compile-time, compatible with Java 5, preferably in the form of class files, preferably optimized for standard JVMs, and its dependencies declared externally but:
  - Variant 'apiElementsJdk6' capability org:producer:1.0 declares a library for use during compile-time, packaged as a jar, and its dependencies declared externally:
      - Incompatible because this component declares a component, compatible with Java 6 and the consumer needed a component, compatible with Java 5
      - Other compatible attribute:
          - Doesn't say anything about its target Java environment (preferred optimized for standard JVMs)
  - Variant 'apiElementsJdk7' capability org:producer:1.0 declares a library for use during compile-time, packaged as a jar, and its dependencies declared externally:
      - Incompatible because this component declares a component, compatible with Java 7 and the consumer needed a component, compatible with Java 5
      - Other compatible attribute:
          - Doesn't say anything about its target Java environment (preferred optimized for standard JVMs)
  - Variant 'apiElementsJdk9' capability org:producer:1.0 declares a library for use during compile-time, packaged as a jar, and its dependencies declared externally:
      - Incompatible because this component declares a component, compatible with Java 9 and the consumer needed a component, compatible with Java 5
      - Other compatible attribute:
          - Doesn't say anything about its target Java environment (preferred optimized for standard JVMs)
  - Variant 'runtimeElementsJdk6' capability org:producer:1.0 declares a library for use during runtime, packaged as a jar, and its dependencies declared externally:
      - Incompatible because this component declares a component, compatible with Java 6 and the consumer needed a component, compatible with Java 5
      - Other compatible attribute:
          - Doesn't say anything about its target Java environment (preferred optimized for standard JVMs)
  - Variant 'runtimeElementsJdk7' capability org:producer:1.0 declares a library for use during runtime, packaged as a jar, and its dependencies declared externally:
      - Incompatible because this component declares a component, compatible with Java 7 and the consumer needed a component, compatible with Java 5
      - Other compatible attribute:
          - Doesn't say anything about its target Java environment (preferred optimized for standard JVMs)
  - Variant 'runtimeElementsJdk9' capability org:producer:1.0 declares a library for use during runtime, packaged as a jar, and its dependencies declared externally:
      - Incompatible because this component declares a component, compatible with Java 9 and the consumer needed a component, compatible with Java 5
      - Other compatible attribute:
          - Doesn't say anything about its target Java environment (preferred optimized for standard JVMs)''')
    }

    def "can select the most appropriate producer variant (#expected) based on target compatibility (#requested)"() {
        buildFile << """
            configurations.compileClasspath.attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, $requested)
        """

        when:
        module.pom.expectGet()
        module.moduleMetadata.expectGet()
        module.getArtifact(classifier: "jdk${selected}").expectGet()

        run ':checkDeps'

        then:
        resolve.expectGraph {
            root(":", ":test:") {
                module('org:producer:1.0') {
                    variant(expected, [
                            'org.gradle.dependency.bundling': 'external',
                            'org.gradle.jvm.version': selected,
                            'org.gradle.usage': 'java-api',
                            'org.gradle.category': 'library',
                            'org.gradle.libraryelements': 'jar',
                            'org.gradle.status': 'release'
                    ])
                    artifact(classifier: "jdk${selected}")
                }
            }
        }

        where:
        requested | selected
        6         | 6
        7         | 7
        8         | 7
        9         | 9
        10        | 9

        expected = "apiElementsJdk$selected"
    }
}
