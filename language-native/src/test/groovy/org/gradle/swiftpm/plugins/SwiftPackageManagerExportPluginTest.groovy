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

import org.gradle.language.swift.SwiftVersion
import org.gradle.nativeplatform.Linkage
import org.gradle.swiftpm.internal.DefaultExecutableProduct
import org.gradle.swiftpm.internal.DefaultLibraryProduct
import org.gradle.swiftpm.tasks.GenerateSwiftPackageManagerManifest
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import spock.lang.Specification

class SwiftPackageManagerExportPluginTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())
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

    def "attaches a swift pm package model to the generate task"() {
        when:
        project.pluginManager.apply(SwiftPackageManagerExportPlugin)
        def generateManifest = project.tasks['generateSwiftPmManifest']

        then:
        !generateManifest.package.present

        when:
        project.evaluate()

        then:
        def p = generateManifest.package.get()
        p != null
        p == generateManifest.package.get()
    }

    def "adds an executable product for each project that produces a C++ application"() {
        given:
        def app1Project = ProjectBuilder.builder().withName("app1").withParent(project).build()
        def app2Project = ProjectBuilder.builder().withName("app2").withParent(project).build()

        project.pluginManager.apply(SwiftPackageManagerExportPlugin)

        app1Project.pluginManager.apply("cpp-application")
        app2Project.pluginManager.apply("cpp-application")

        project.evaluate()

        expect:
        def generateManifest = project.tasks['generateSwiftPmManifest']
        def products = generateManifest.package.get().products
        products.name == ["app1", "app2"]
        products.target.name == ["app1", "app2"]
        products.target.publicHeaderDir == [null, null]
        products.every { it instanceof DefaultExecutableProduct }
    }

    def "adds a library product for each project that produces a C++ library"() {
        given:
        def lib1Project = ProjectBuilder.builder().withName("lib1").withParent(project).build()
        def lib2Project = ProjectBuilder.builder().withName("lib2").withParent(project).build()

        project.pluginManager.apply(SwiftPackageManagerExportPlugin)

        lib1Project.pluginManager.apply("cpp-library")
        lib2Project.pluginManager.apply("cpp-library")

        project.evaluate()

        expect:
        def generateManifest = project.tasks['generateSwiftPmManifest']
        def products = generateManifest.package.get().products
        products.name == ["lib1", "lib2"]
        products.target.name == ["lib1", "lib2"]
        products.target.publicHeaderDir == [lib1Project.file("src/main/public"), lib2Project.file("src/main/public")]
        products.every { it instanceof DefaultLibraryProduct }
    }

    def "adds a library product for each linkage of a C++ library"() {
        given:
        def lib1Project = ProjectBuilder.builder().withName("lib1").withParent(project).build()

        project.pluginManager.apply(SwiftPackageManagerExportPlugin)

        lib1Project.pluginManager.apply("cpp-library")
        lib1Project.library.linkage = [Linkage.SHARED, Linkage.STATIC]

        project.evaluate()

        expect:
        def generateManifest = project.tasks['generateSwiftPmManifest']
        def targets = generateManifest.package.get().targets
        targets.name == ["lib1"]
        def products = generateManifest.package.get().products
        products.name == ["lib1", "lib1Static"]
        products.target == [targets.first(), targets.first()]
        products.every { it instanceof DefaultLibraryProduct }
    }

    def "adds an executable product for each project that produces a Swift application"() {
        given:
        def app1Project = ProjectBuilder.builder().withName("app1").withParent(project).build()
        def app2Project = ProjectBuilder.builder().withName("app2").withParent(project).build()

        project.pluginManager.apply(SwiftPackageManagerExportPlugin)

        app1Project.pluginManager.apply("swift-application")
        app2Project.pluginManager.apply("swift-application")

        project.evaluate()

        expect:
        def generateManifest = project.tasks['generateSwiftPmManifest']
        def products = generateManifest.package.get().products
        products.name == ["app1", "app2"]
        products.target.name == ["App1", "App2"]
        products.target.publicHeaderDir == [null, null]
        products.every { it instanceof DefaultExecutableProduct }
    }

    def "adds a library product for each project that produces a Swift library"() {
        given:
        def lib1Project = ProjectBuilder.builder().withName("lib1").withParent(project).build()
        def lib2Project = ProjectBuilder.builder().withName("lib2").withParent(project).build()

        project.pluginManager.apply(SwiftPackageManagerExportPlugin)

        lib1Project.pluginManager.apply("swift-library")
        lib2Project.pluginManager.apply("swift-library")

        project.evaluate()

        expect:
        def generateManifest = project.tasks['generateSwiftPmManifest']
        def products = generateManifest.package.get().products
        products.name == ["lib1", "lib2"]
        products.target.name == ["Lib1", "Lib2"]
        products.target.publicHeaderDir == [null, null]
        products.every { it instanceof DefaultLibraryProduct }
    }

    def "adds a library product for each linkage of a Swift library"() {
        given:
        def lib1Project = ProjectBuilder.builder().withName("lib1").withParent(project).build()

        project.pluginManager.apply(SwiftPackageManagerExportPlugin)

        lib1Project.pluginManager.apply("swift-library")
        lib1Project.library.linkage = [Linkage.SHARED, Linkage.STATIC]

        project.evaluate()

        expect:
        def generateManifest = project.tasks['generateSwiftPmManifest']
        def targets = generateManifest.package.get().targets
        targets.name == ["Lib1"]
        def products = generateManifest.package.get().products
        products.name == ["lib1", "lib1Static"]
        products.target == [targets.first(), targets.first()]
        products.every { it instanceof DefaultLibraryProduct }
    }

    def "includes swift language version"() {
        given:
        def lib1Project = ProjectBuilder.builder().withName("lib1").withParent(project).build()

        project.pluginManager.apply(SwiftPackageManagerExportPlugin)

        lib1Project.pluginManager.apply("swift-application")
        lib1Project.application.sourceCompatibility = SwiftVersion.SWIFT4

        project.evaluate()

        expect:
        def generateManifest = project.tasks['generateSwiftPmManifest']
        def pkg = generateManifest.package.get()
        pkg.swiftLanguageVersion == SwiftVersion.SWIFT4
    }

    def "does not include a swift language version if not explicitly declared"() {
        given:
        def lib1Project = ProjectBuilder.builder().withName("lib1").withParent(project).build()
        def lib2Project = ProjectBuilder.builder().withName("lib2").withParent(project).build()

        project.pluginManager.apply(SwiftPackageManagerExportPlugin)

        lib1Project.pluginManager.apply("swift-application")
        lib2Project.pluginManager.apply("swift-library")

        project.evaluate()

        expect:
        def generateManifest = project.tasks['generateSwiftPmManifest']
        def pkg = generateManifest.package.get()
        pkg.swiftLanguageVersion == null
    }

    def "uses max swift language version"() {
        given:
        def lib1Project = ProjectBuilder.builder().withName("lib1").withParent(project).build()
        def lib2Project = ProjectBuilder.builder().withName("lib2").withParent(project).build()
        def lib3Project = ProjectBuilder.builder().withName("lib3").withParent(project).build()

        project.pluginManager.apply(SwiftPackageManagerExportPlugin)

        project.pluginManager.apply("swift-application")
        lib1Project.pluginManager.apply("swift-library")
        lib2Project.pluginManager.apply("swift-library")
        lib3Project.pluginManager.apply("swift-library")

        project.application.sourceCompatibility = SwiftVersion.SWIFT3
        lib1Project.library.sourceCompatibility = SwiftVersion.SWIFT4
        lib2Project.library.sourceCompatibility = SwiftVersion.SWIFT3

        project.evaluate()

        expect:
        def generateManifest = project.tasks['generateSwiftPmManifest']
        def pkg = generateManifest.package.get()
        pkg.swiftLanguageVersion == SwiftVersion.SWIFT4
    }

    def "ignores projects that do not define any C++ or Swift components"() {
        given:
        ProjectBuilder.builder().withName("app1").withParent(project).build()
        ProjectBuilder.builder().withName("app2").withParent(project).build()

        project.pluginManager.apply(SwiftPackageManagerExportPlugin)
        project.pluginManager.apply("swift-library")

        project.evaluate()

        expect:
        def generateManifest = project.tasks['generateSwiftPmManifest']
        def products = generateManifest.package.get().products
        products.name == ["testLib"]
    }

    def "maps project dependency to a target in referenced project"() {
        given:
        def lib1 = ProjectBuilder.builder().withName("lib1").withParent(project).build()

        project.pluginManager.apply(SwiftPackageManagerExportPlugin)
        project.pluginManager.apply("swift-library")
        lib1.pluginManager.apply("swift-library")

        project.dependencies.add("implementation", lib1)

        project.evaluate()

        expect:
        def generateManifest = project.tasks["generateSwiftPmManifest"]
        def targets = generateManifest.package.get().targets
        def target = targets.find { it.name == "TestLib" }
        target.requiredTargets == ["Lib1"]
    }
}
