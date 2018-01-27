/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.swiftpm.plugins

import org.gradle.swiftpm.internal.DefaultExecutableProduct
import org.gradle.swiftpm.internal.DefaultLibraryProduct
import org.gradle.swiftpm.tasks.GenerateSwiftPackageManagerManifest
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import spock.lang.Specification

class SwiftPackageManagerExportPluginTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    def projectDir = tmpDir.createDir("project")
    def project = ProjectBuilder.builder().withProjectDir(projectDir).withName("testLib").build()

    def "adds generate task"() {
        given:
        project.pluginManager.apply(SwiftPackageManagerExportPlugin)

        expect:
        def generateManifest = project.tasks['generateSwiftPmManifest']
        generateManifest instanceof GenerateSwiftPackageManagerManifest
        generateManifest.manifestFile.get().asFile == project.file("Package.swift")
    }

    def "adds an executable product for each project that produces a C++ application"() {
        given:
        def app1Project = ProjectBuilder.builder().withName("app1").withParent(project).build()
        def app2Project = ProjectBuilder.builder().withName("app2").withParent(project).build()

        project.pluginManager.apply(SwiftPackageManagerExportPlugin)

        app1Project.pluginManager.apply("cpp-application")
        app2Project.pluginManager.apply("cpp-application")

        expect:
        def generateManifest = project.tasks['generateSwiftPmManifest']
        def products = generateManifest.package.get().products
        products.name == ["app1", "app2"]
        products.targetName == ["app1", "app2"]
        products.publicHeaderDir == [null, null]
        products.every { it instanceof DefaultExecutableProduct }
    }

    def "adds a library product for each project that produces a C++ library"() {
        given:
        def app1Project = ProjectBuilder.builder().withName("lib1").withParent(project).build()
        def app2Project = ProjectBuilder.builder().withName("lib2").withParent(project).build()

        project.pluginManager.apply(SwiftPackageManagerExportPlugin)

        app1Project.pluginManager.apply("cpp-library")
        app2Project.pluginManager.apply("cpp-library")

        expect:
        def generateManifest = project.tasks['generateSwiftPmManifest']
        def products = generateManifest.package.get().products
        products.name == ["lib1", "lib2"]
        products.targetName == ["lib1", "lib2"]
        products.publicHeaderDir == [app1Project.file("src/main/public"), app2Project.file("src/main/public")]
        products.every { it instanceof DefaultLibraryProduct }
    }

    def "adds an executable product for each project that produces a Swift application"() {
        given:
        def app1Project = ProjectBuilder.builder().withName("app1").withParent(project).build()
        def app2Project = ProjectBuilder.builder().withName("app2").withParent(project).build()

        project.pluginManager.apply(SwiftPackageManagerExportPlugin)

        app1Project.pluginManager.apply("swift-application")
        app2Project.pluginManager.apply("swift-application")

        expect:
        def generateManifest = project.tasks['generateSwiftPmManifest']
        def products = generateManifest.package.get().products
        products.name == ["app1", "app2"]
        products.targetName == ["App1", "App2"]
        products.publicHeaderDir == [null, null]
        products.every { it instanceof DefaultExecutableProduct }
    }

    def "adds a library product for each project that produces a Swift library"() {
        given:
        def app1Project = ProjectBuilder.builder().withName("lib1").withParent(project).build()
        def app2Project = ProjectBuilder.builder().withName("lib2").withParent(project).build()

        project.pluginManager.apply(SwiftPackageManagerExportPlugin)

        app1Project.pluginManager.apply("swift-library")
        app2Project.pluginManager.apply("swift-library")

        expect:
        def generateManifest = project.tasks['generateSwiftPmManifest']
        def products = generateManifest.package.get().products
        products.name == ["lib1", "lib2"]
        products.targetName == ["Lib1", "Lib2"]
        products.publicHeaderDir == [null, null]
        products.every { it instanceof DefaultLibraryProduct }
    }

    def "ignores projects that do not define any C++ or Swift components"() {
        given:
        ProjectBuilder.builder().withName("app1").withParent(project).build()
        ProjectBuilder.builder().withName("app2").withParent(project).build()

        project.pluginManager.apply(SwiftPackageManagerExportPlugin)
        project.pluginManager.apply("swift-library")

        expect:
        def generateManifest = project.tasks['generateSwiftPmManifest']
        def products = generateManifest.package.get().products
        products.name == ["testLib"]
    }

}
