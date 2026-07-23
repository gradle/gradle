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

package org.gradle.plugins.ide.tooling.r70

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.JdkVersionTestPreconditions
import org.gradle.tooling.model.eclipse.EclipseClasspathEntry
import org.gradle.tooling.model.eclipse.EclipseProject

import static org.gradle.test.fixtures.jpms.ModuleJarFixture.autoModuleJar
import static org.gradle.test.fixtures.jpms.ModuleJarFixture.moduleJar
import static org.gradle.test.fixtures.jpms.ModuleJarFixture.traditionalJar

// The JPMS "module" classpath attribute on external dependencies relies on module-path inference, which is enabled by default from 7.0.
@Requires(JdkVersionTestPreconditions.Jdk9OrLater)
@TargetGradleVersion(">=7.0")
class ToolingApiEclipseModelJavaModulesCrossVersionSpec extends ToolingApiSpecification {

    def "external dependencies are not marked as modules when the consuming project is not modular"() {
        setup:
        def mavenRepo = publishModules()
        buildFile << buildScriptWithModuleDependencies(mavenRepo)

        when:
        EclipseProject project = loadToolingModel(EclipseProject)

        then:
        !isModule(library(project, 'jmodule-1.0.jar'))
        !isModule(library(project, 'jautomodule-1.0.jar'))
        !isModule(library(project, 'jlib-1.0.jar'))
    }

    def "modules and auto-modules on the classpath are marked when the consuming project is modular"() {
        setup:
        def mavenRepo = publishModules()
        buildFile << buildScriptWithModuleDependencies(mavenRepo)
        file("src/main/java/module-info.java") << """
            module my.module {
                requires jmodule;
                requires jautomodule;
            }
        """

        when:
        EclipseProject project = loadToolingModel(EclipseProject)

        then:
        isModule(library(project, 'jmodule-1.0.jar'))
        isModule(library(project, 'jautomodule-1.0.jar'))
        !isModule(library(project, 'jlib-1.0.jar'))
    }

    private MavenFileRepository publishModules() {
        def mavenRepo = new MavenFileRepository(file("maven-repo"))
        mavenRepo.module('org', 'jmodule', '1.0').mainArtifact(content: moduleJar('jmodule')).publish()
        mavenRepo.module('org', 'jautomodule', '1.0').mainArtifact(content: autoModuleJar('jautomodule')).publish()
        mavenRepo.module('org', 'jlib', '1.0').mainArtifact(content: traditionalJar('jlib')).publish()
        mavenRepo
    }

    private static String buildScriptWithModuleDependencies(MavenFileRepository mavenRepo) {
        """
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

    private static EclipseClasspathEntry library(EclipseProject project, String jarName) {
        project.classpath.find { it.file.name == jarName }
    }

    private static boolean isModule(EclipseClasspathEntry entry) {
        entry.classpathAttributes.find { it.name == 'module' && it.value == 'true' }
    }
}
