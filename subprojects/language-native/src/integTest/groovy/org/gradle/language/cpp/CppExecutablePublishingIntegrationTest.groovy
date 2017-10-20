/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.language.cpp

import org.gradle.nativeplatform.fixtures.app.CppApp
import org.gradle.nativeplatform.fixtures.app.CppAppWithLibrary
import org.gradle.test.fixtures.maven.MavenFileModule
import org.gradle.test.fixtures.maven.MavenFileRepository

class CppExecutablePublishingIntegrationTest extends AbstractCppInstalledToolChainIntegrationTest implements CppTaskNames {
    def "can publish the binaries of an application to a Maven repository"() {
        def app = new CppApp()

        given:
        buildFile << """
            apply plugin: 'cpp-executable'
            apply plugin: 'maven-publish'
            
            group = 'some.group'
            version = '1.2'
            executable {
                baseName = 'test'
            }
            publishing {
                repositories { maven { url 'repo' } }
            }
"""
        app.writeToProject(testDirectory)

        when:
        run('publish')

        then:
        result.assertTasksExecuted(
            compileAndLinkTasks(debug),
            compileAndLinkTasks(release),
            compileAndLinkStaticTasks(debug),
            compileAndLinkStaticTasks(release),
            publishTasks(debug),
            publishTasks(release),
            publishTasks('', debug, staticLinkage),
            publishTasks('', release, staticLinkage),

            ":generatePomFileForMainPublication",
            ":generateMetadataFileForMainPublication",
            ":publishMainPublicationToMavenRepository",

            ":publish")

        def repo = new MavenFileRepository(file("repo"))

        def main = repo.module('some.group', 'test', '1.2')
        main.assertPublished()
        main.assertArtifactsPublished("test-1.2.pom", "test-1.2-module.json")
        main.parsedPom.scopes.isEmpty()
        def mainMetadata = main.parsedModuleMetadata
        mainMetadata.variants.size() == 4
        mainMetadata.variant("debugShared-runtime").availableAt.coords == "some.group:test_debugShared:1.2"
        mainMetadata.variant("releaseShared-runtime").availableAt.coords == "some.group:test_releaseShared:1.2"
        mainMetadata.variant("debugStatic-runtime").availableAt.coords == "some.group:test_debugStatic:1.2"
        mainMetadata.variant("releaseStatic-runtime").availableAt.coords == "some.group:test_releaseStatic:1.2"

        assertTestModulePublished(repo, "debug", "shared")
        assertTestModulePublished(repo, "release", "shared")
        assertTestModulePublished(repo, "debug", "static")
        assertTestModulePublished(repo, "release", "static")

        when:
        def consumer = file("consumer").createDir()
        consumer.file("build.gradle") << """
            repositories {
                maven { 
                    url '${repo.uri}' 
                    useGradleMetadata()
                }
            }
            configurations {
                install {
                    attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, 'native-runtime'))
                    attributes.attribute(Attribute.of('org.gradle.native.debuggable', Boolean), true)
                    attributes.attribute(Attribute.of('org.gradle.native.linkage', org.gradle.language.cpp.Linkage), org.gradle.language.cpp.Linkage.SHARED)
                }
            }
            dependencies {
                install 'some.group:test:1.2'
            }
            task install(type: Sync) {
                from configurations.install
                into 'install'
            }
"""
        executer.inDirectory(consumer)
        run("install")

        then:
        def executable = executable("consumer/install/test")
        executable.file.setExecutable(true)
        executable.exec().out == app.expectedOutput
    }

    private void assertTestModulePublished(MavenFileRepository repo, String buildType, String linkage) {
        def variant = "$buildType${linkage.capitalize()}"
        def module = repo.module('some.group', "test_$variant", '1.2')
        module.assertPublished()
        module.assertArtifactsPublished(executableName("test_${variant}-1.2"), "test_${variant}-1.2.pom", "test_${variant}-1.2-module.json")
        module.artifactFile(type: executableExtension).assertIsCopyOf(executable("build/exe/main/$buildType/$linkage/test").file)

        assert module.parsedPom.scopes.isEmpty()

        def metadata = module.parsedModuleMetadata
        assert metadata.variants.size() == 1
        def runtime = metadata.variant("${variant}-runtime")
        assert runtime.dependencies.empty
        assert runtime.files.size() == 1
        assert runtime.files[0].name == executableName('test')
        assert runtime.files[0].url == executableName("test_${variant}-1.2")
    }


    def "can publish an executable and library to a Maven repository"() {
        def app = new CppAppWithLibrary()

        given:
        settingsFile << "include 'greeter', 'app'"
        buildFile << """
            subprojects {
                apply plugin: 'maven-publish'
                
                group = 'some.group'
                version = '1.2'
                publishing {
                    repositories { maven { url '../repo' } }
                }
            }
            project(':app') { 
                apply plugin: 'cpp-executable'
                dependencies {
                    implementation project(':greeter')
                }
            }
            project(':greeter') { 
                apply plugin: 'cpp-library'
            }
"""
        app.greeter.writeToProject(file('greeter'))
        app.main.writeToProject(file('app'))

        when:
        run('publish')

        then:
        def repo = new MavenFileRepository(file("repo"))
        assertAppModulesArePublished(repo)
        assertGreeterModulesArePublished(repo)

        when:
        def consumer = file("consumer").createDir()
        consumer.file("settings.gradle") << ''
        consumer.file("build.gradle") << """
            repositories {
                maven { 
                    url '${repo.uri}' 
                    useGradleMetadata()
                }
            }
            configurations {
                install {
                    attributes.attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, 'native-runtime'))
                    attributes.attribute(Attribute.of('org.gradle.native.debuggable', Boolean), true)
                    attributes.attribute(Attribute.of('org.gradle.native.linkage', org.gradle.language.cpp.Linkage), org.gradle.language.cpp.Linkage.SHARED)
                }
            }
            dependencies {
                install 'some.group:app:1.2'
            }
            task install(type: Sync) {
                from configurations.install
                into 'install'
            }
"""
        executer.inDirectory(consumer)
        run("install")

        then:
        def executable = executable("consumer/install/app")
        executable.file.setExecutable(true)
        executable.exec().out == app.expectedOutput
    }

    private void assertGreeterModulesArePublished(MavenFileRepository repo) {
        assertModulePublishedWithNoDependencies(repo.module('some.group', 'greeter', '1.2'))
        assertModulePublishedWithNoDependencies(repo.module('some.group', 'greeter_debugShared', '1.2'))
        assertModulePublishedWithNoDependencies(repo.module('some.group', 'greeter_releaseShared', '1.2'))
        assertModulePublishedWithNoDependencies(repo.module('some.group', 'greeter_debugStatic', '1.2'))
        assertModulePublishedWithNoDependencies(repo.module('some.group', 'greeter_releaseStatic', '1.2'))
    }

    private void assertModulePublishedWithNoDependencies(MavenFileModule module) {
        module.assertPublished()
        assert module.parsedPom.scopes.isEmpty()
    }

    private void assertAppModulesArePublished(MavenFileRepository repo) {
        def appModule = repo.module('some.group', 'app', '1.2')
        appModule.assertPublished()

        assertModulePublishedWithDependencyOnGreeter(
            repo.module('some.group', 'app_debugShared', '1.2'))
        assertModulePublishedWithDependencyOnGreeter(
            repo.module('some.group', 'app_releaseShared', '1.2'))
        assertModulePublishedWithNoDependencies(
            repo.module('some.group', 'app_debugStatic', '1.2'))
        assertModulePublishedWithNoDependencies(
            repo.module('some.group', 'app_releaseStatic', '1.2'))
    }

    private void assertModulePublishedWithDependencyOnGreeter(MavenFileModule module) {
        def variant = module.artifactId.substring(module.artifactId.indexOf('_') + 1)

        module.assertPublished()
        assert module.parsedPom.scopes.size() == 1
        module.parsedPom.scopes.runtime.assertDependsOn("some.group:greeter:1.2")

        def metadata = module.parsedModuleMetadata
        def runtime = metadata.variant("${variant}-runtime")
        assert runtime.dependencies.size() == 1
        assert runtime.dependencies[0].group == 'some.group'
        assert runtime.dependencies[0].module == 'greeter'
        assert runtime.dependencies[0].version == '1.2'
    }
}
