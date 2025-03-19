/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.plugins.ide.eclipse

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.test.fixtures.maven.MavenModule
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

import static org.gradle.test.fixtures.jpms.ModuleJarFixture.autoModuleJar
import static org.gradle.test.fixtures.jpms.ModuleJarFixture.moduleJar
import static org.gradle.test.fixtures.jpms.ModuleJarFixture.traditionalJar

@Requires(UnitTestPreconditions.Jdk9OrLater)
class EclipseJavaModulesIntegrationTest extends AbstractEclipseIntegrationSpec {

    def setup() {
        publishJavaModule('jmodule')
        publishAutoModule('jautomodule')
        publishJavaLibrary('jlib')

        buildFile << """
            plugins {
                id 'java-library'
                id 'eclipse'
            }
            repositories {
                maven { url = "${mavenRepo.uri}" }
            }

            dependencies {
                implementation 'org:jmodule:1.0'
                implementation 'org:jautomodule:1.0'
                implementation 'org:jlib:1.0'
            }
        """
    }

    @ToBeFixedForConfigurationCache
    def "dependencies are not marked as modules if the project itself is not modular"() {
        when:
        succeeds "eclipse"

        then:
        def libraries = classpath.libs
        libraries.size() == 3
        libraries[0].jarName == 'jmodule-1.0.jar'
        libraries[0].assertHasNoAttribute('module', 'true')
        libraries[1].jarName == 'jautomodule-1.0.jar'
        libraries[1].assertHasNoAttribute('module', 'true')
        libraries[2].jarName == 'jlib-1.0.jar'
        libraries[2].assertHasNoAttribute('module', 'true')
    }

    @ToBeFixedForConfigurationCache
    def "Marks modules on classpath as such"() {
        setup:
        file("src/main/java/module-info.java") << """
            module my.module {
                requires jmodule
                requires jautomodule
            }
        """

        when:
        succeeds "eclipse"

        then:
        def libraries = classpath.libs
        libraries.size() == 3
        libraries[0].jarName == 'jmodule-1.0.jar'
        libraries[0].assertHasAttribute('module', 'true')
        libraries[1].jarName == 'jautomodule-1.0.jar'
        libraries[1].assertHasAttribute('module', 'true')
        libraries[2].jarName == 'jlib-1.0.jar'
        libraries[2].assertHasNoAttribute('module', 'true')
    }

    private MavenModule publishJavaModule(String name) {
        mavenRepo.module('org', name, '1.0').mainArtifact(content: moduleJar(name)).publish()
    }

    private MavenModule publishJavaLibrary(String name) {
        mavenRepo.module('org', name, '1.0').mainArtifact(content: traditionalJar(name)).publish()
    }

    private MavenModule publishAutoModule(String name) {
        mavenRepo.module('org', name, '1.0').mainArtifact(content: autoModuleJar(name)).publish()
    }
}
