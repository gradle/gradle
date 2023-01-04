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

package org.gradle.swiftpm

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.vcs.fixtures.GitFileRepository


class SwiftPackageManagerDependencyMappingIntegrationTest extends AbstractSwiftPackageManagerExportIntegrationTest {
    def "export fails when external dependency cannot be mapped to a git url"() {
        given:
        buildFile << """
            plugins {
                id 'swiftpm-export'
                id 'swift-library'
            }
            dependencies {
                implementation 'dep:dep:1.0'
            }
"""

        when:
        fails("generateSwiftPmManifest")

        then:
        failure.assertHasCause("Cannot determine the Git URL for dependency on dep:dep.")
    }

    def "export fails when file dependency is present"() {
        given:
        buildFile << """
            plugins {
                id 'swiftpm-export'
                id 'swift-library'
            }
            dependencies {
                implementation files("abc.swiftmodule")
            }
"""

        when:
        fails("generateSwiftPmManifest")

        then:
        failure.assertHasCause("Cannot map a dependency of type ")
    }

    @ToBeFixedForConfigurationCache
    def "export fails when external dependency defines both branch and version constraint"() {
        given:
        buildFile << """
            plugins {
                id 'swiftpm-export'
                id 'swift-library'
            }
            dependencies {
                implementation('dep:dep:1.0') { versionConstraint.branch = 'release' }
            }
"""
        settingsFile << """
            sourceControl {
                vcsMappings {
                    withModule("dep:dep") {
                        from(GitVersionControlSpec) {
                            url = uri('repo')
                        }
                    }
                }
            }
"""

        when:
        fails("generateSwiftPmManifest")

        then:
        failure.assertHasCause("Cannot map a dependency on dep:dep that defines both a branch (release) and a version constraint (1.0).")
    }

    @ToBeFixedForConfigurationCache(because = "Task.getProject() during execution")
    def "produces manifest for Swift component with dependencies on multiple repositories"() {
        given:
        def lib1Repo = GitFileRepository.init(testDirectory.file("repos/lib1"))
        lib1Repo.file("build.gradle") << """
            plugins {
                id 'swift-library'
                id 'swiftpm-export'
            }
        """
        lib1Repo.file("settings.gradle") << "rootProject.name = 'lib1'"
        lib1Repo.file("src/main/swift/Lib1.swift") << """
            public class Lib1 {
                public class func thing() { }
            }
"""
        executer.inDirectory(lib1Repo.workTree).withTasks("generateSwiftPmManifest").run()
        lib1Repo.commit("v1")
        lib1Repo.createLightWeightTag("1.0.0")

        and:
        def lib2Repo = GitFileRepository.init(testDirectory.file("repos/lib2"))
        lib2Repo.file("build.gradle") << """
            plugins {
                id 'swift-library'
                id 'swiftpm-export'
            }
        """
        lib2Repo.file("settings.gradle") << "rootProject.name = 'lib2'"
        lib2Repo.file("src/main/swift/Lib2.swift") << """
            public class Lib2 {
                public class func thing() { }
            }
        """
        executer.inDirectory(lib2Repo.workTree).withTasks("generateSwiftPmManifest").run()
        lib2Repo.commit("v2")
        lib2Repo.createLightWeightTag("2.0.0")

        and:
        settingsFile << """
            sourceControl {
                vcsMappings {
                    withModule("test:lib1") {
                        from(GitVersionControlSpec) {
                            url = uri('${lib1Repo.url}')
                        }
                    }
                    withModule("test:lib2") {
                        from(GitVersionControlSpec) {
                            url = uri('${lib2Repo.url}')
                        }
                    }
                }
            }
"""
        buildFile << """
            plugins {
                id 'swift-library'
                id 'swiftpm-export'
            }
            dependencies {
                api "test:lib1:1.0.0"
                implementation "test:lib2:2.0.0"
            }
"""
        file("src/main/swift/Lib.swift") << """
            import Lib1
            import Lib2
            class Lib {
                init() {
                    Lib1.thing()
                    Lib2.thing()
                }
            }
        """

        when:
        run("generateSwiftPmManifest")

        then:
        file("Package.swift").text == """// swift-tools-version:4.0
//
// GENERATED FILE - do not edit
//
import PackageDescription

let package = Package(
    name: "test",
    products: [
        .library(name: "test", type: .dynamic, targets: ["Test"]),
    ],
    dependencies: [
        .package(url: "repos/lib2", from: "2.0.0"),
        .package(url: "repos/lib1", from: "1.0.0"),
    ],
    targets: [
        .target(
            name: "Test",
            dependencies: [
                .product(name: "lib2"),
                .product(name: "lib1"),
            ],
            path: ".",
            sources: [
                "src/main/swift/Lib.swift",
            ]
        ),
    ]
)
"""
        swiftPmBuildSucceeds()

        cleanup:
        lib1Repo?.close()
        lib2Repo?.close()
    }

    @ToBeFixedForConfigurationCache(because = "Task.getProject() during execution")
    def "produces manifest for Swift component with dependencies on libraries provided by included builds"() {
        given:
        def lib1Repo = GitFileRepository.init(testDirectory.file("repos/lib1"))
        lib1Repo.file("build.gradle") << """
            plugins {
                id 'swift-library'
                id 'swiftpm-export'
            }
        """
        lib1Repo.file("settings.gradle") << "rootProject.name = 'lib1'"
        lib1Repo.file("src/main/swift/Lib1.swift") << """
            public class Lib1 {
                public class func thing() { }
            }
"""
        executer.inDirectory(lib1Repo.workTree).withTasks("generateSwiftPmManifest").run()
        lib1Repo.commit("v1")
        lib1Repo.createLightWeightTag("1.0.0")
        lib1Repo.commit("v2")

        and:
        def lib2Repo = GitFileRepository.init(testDirectory.file("repos/lib2"))
        lib2Repo.file("build.gradle") << """
            plugins {
                id 'swift-library'
                id 'swiftpm-export'
            }
        """
        lib2Repo.file("settings.gradle") << "rootProject.name = 'lib2'"
        lib2Repo.file("src/main/swift/Lib2.swift") << """
            public class Lib2 {
                public class func thing() { }
            }
        """
        executer.inDirectory(lib2Repo.workTree).withTasks("generateSwiftPmManifest").run()
        lib2Repo.commit("v2")
        lib2Repo.createLightWeightTag("2.0.0")
        lib2Repo.commit("v3")

        and:
        settingsFile << """
            includeBuild 'repos/lib1'
            includeBuild 'repos/lib2'

            sourceControl {
                vcsMappings {
                    withModule("test:lib1") {
                        from(GitVersionControlSpec) {
                            url = uri('${lib1Repo.url}')
                        }
                    }
                    withModule("test:lib2") {
                        from(GitVersionControlSpec) {
                            url = uri('${lib2Repo.url}')
                        }
                    }
                }
            }
"""
        buildFile << """
            plugins {
                id 'swift-library'
                id 'swiftpm-export'
            }
            dependencies {
                api "test:lib1:1.0.0"
                implementation "test:lib2:2.0.0"
            }
"""
        file("src/main/swift/Lib.swift") << """
            import Lib1
            import Lib2
            class Lib {
                init() {
                    Lib1.thing()
                    Lib2.thing()
                }
            }
        """

        when:
        run("generateSwiftPmManifest")

        then:
        file("Package.swift").text == """// swift-tools-version:4.0
//
// GENERATED FILE - do not edit
//
import PackageDescription

let package = Package(
    name: "test",
    products: [
        .library(name: "test", type: .dynamic, targets: ["Test"]),
    ],
    dependencies: [
        .package(url: "repos/lib2", from: "2.0.0"),
        .package(url: "repos/lib1", from: "1.0.0"),
    ],
    targets: [
        .target(
            name: "Test",
            dependencies: [
                .product(name: "lib2"),
                .product(name: "lib1"),
            ],
            path: ".",
            sources: [
                "src/main/swift/Lib.swift",
            ]
        ),
    ]
)
"""
        swiftPmBuildSucceeds()

        cleanup:
        lib1Repo?.close()
        lib2Repo?.close()
    }

    @ToBeFixedForConfigurationCache(because = "Task.getProject() during execution")
    def "maps dependency on #src to #mapped"() {
        given:
        def lib1Repo = GitFileRepository.init(testDirectory.file("repo/lib1"))
        lib1Repo.file("build.gradle") << """
            plugins {
                id 'swift-library'
                id 'swiftpm-export'
            }
        """
        lib1Repo.file("settings.gradle") << "rootProject.name = 'lib1'"
        lib1Repo.file("src/main/swift/Lib1.swift") << """
            public class Lib1 {
                public class func thing() { }
            }
"""
        executer.inDirectory(lib1Repo.workTree).withTasks("generateSwiftPmManifest").run()
        lib1Repo.commit("v1")
        lib1Repo.createLightWeightTag("1.2.0")

        settingsFile << """
            sourceControl {
                vcsMappings {
                    withModule("dep:lib1") {
                        from(GitVersionControlSpec) {
                            url = uri('${lib1Repo.url}')
                        }
                    }
                }
            }
        """
        buildFile << """
            plugins {
                id 'swiftpm-export'
                id 'swift-library'
            }
            dependencies {
                implementation 'dep:lib1:${src}'
            }
        """
        file("src/main/swift/Lib.swift") << """
            import Lib1
            class Lib {
                init() {
                    Lib1.thing()
                }
            }
        """

        when:
        run("generateSwiftPmManifest")

        then:
        file("Package.swift").text.contains("""
    dependencies: [
        .package(url: "repo/lib1", $mapped),
    ],
""")
        swiftPmBuildSucceeds()

        cleanup:
        lib1Repo?.close()

        where:
        src              | mapped
        '1.2.0'          | 'from: "1.2.0"'
        '1.2.+'          | '"1.2.0"..<"1.3.0"'
        '1.+'            | 'from: "1.0.0"'
        '[1.0.0, 2.0.0]' | '"1.0.0"..."2.0.0"'
        '[1.0.0, 2.0.0)' | '"1.0.0"..<"2.0.0"'
    }

    @ToBeFixedForConfigurationCache
    def "cannot map dependency #src"() {
        given:
        settingsFile << """
            sourceControl {
                vcsMappings {
                    withModule("dep:lib1") {
                        from(GitVersionControlSpec) {
                            url = uri('repo')
                        }
                    }
                }
            }
        """
        buildFile << """
            plugins {
                id 'swiftpm-export'
                id 'swift-library'
            }
            dependencies {
                implementation 'dep:lib1:${src}'
            }
        """

        when:
        fails("generateSwiftPmManifest")

        then:
        failure.assertHasCause("Cannot map a dependency on dep:lib1 with version constraint ($src).")

        where:
        src             | _
        '+'             | _
        '1+'            | _
        '1.2+'          | _
        '1.2.3+'        | _
        '1.2.3.+'       | _
        'abc+'          | _
        '(1.0.0,2.0.0]' | _
        '(1.0.0,2.0.0)' | _
    }

    @ToBeFixedForConfigurationCache(because = "Task.getProject() during execution")
    def "maps dependency on latest.integration to master branch"() {
        given:
        def lib1Repo = GitFileRepository.init(testDirectory.file("repo/lib1"))
        lib1Repo.file("build.gradle") << """
            plugins {
                id 'swift-library'
                id 'swiftpm-export'
            }
        """
        lib1Repo.file("settings.gradle") << "rootProject.name = 'lib1'"
        lib1Repo.file("src/main/swift/Lib1.swift") << """
            public class Lib1 {
                public class func thing() { }
            }
"""
        executer.inDirectory(lib1Repo.workTree).withTasks("generateSwiftPmManifest").run()
        lib1Repo.commit("v1")

        settingsFile << """
            sourceControl {
                vcsMappings {
                    withModule("dep:lib1") {
                        from(GitVersionControlSpec) {
                            url = uri('${lib1Repo.url}')
                        }
                    }
                }
            }
        """
        buildFile << """
            plugins {
                id 'swiftpm-export'
                id 'swift-library'
            }
            dependencies {
                implementation 'dep:lib1:latest.integration'
            }
        """
        file("src/main/swift/Lib.swift") << """
            import Lib1
            class Lib {
                init() {
                    Lib1.thing()
                }
            }
        """

        when:
        run("generateSwiftPmManifest")

        then:
        file("Package.swift").text.contains("""
    dependencies: [
        .package(url: "repo/lib1", .branch("master")),
    ],
""")
        swiftPmBuildSucceeds()

        cleanup:
        lib1Repo?.close()
    }

    @ToBeFixedForConfigurationCache(because = "Task.getProject() during execution")
    def "maps dependency on upstream branch"() {
        given:
        def lib1Repo = GitFileRepository.init(testDirectory.file("repo/lib1"))
        lib1Repo.file("build.gradle") << """
            plugins {
                id 'swift-library'
                id 'swiftpm-export'
            }
        """
        lib1Repo.file("settings.gradle") << "rootProject.name = 'lib1'"
        lib1Repo.file("src/main/swift/Lib1.swift") << """
            public class Lib1 {
                public class func thing() { }
            }
"""
        lib1Repo.commit("v1")
        lib1Repo.createBranch("release")
        lib1Repo.checkout("release")
        executer.inDirectory(lib1Repo.workTree).withTasks("generateSwiftPmManifest").run()
        lib1Repo.commit("v2")

        settingsFile << """
            sourceControl {
                vcsMappings {
                    withModule("dep:lib1") {
                        from(GitVersionControlSpec) {
                            url = uri('${lib1Repo.url}')
                        }
                    }
                }
            }
        """
        buildFile << """
            plugins {
                id 'swiftpm-export'
                id 'swift-library'
            }
            dependencies {
                implementation('dep:lib1') {
                    versionConstraint.branch = 'release'
                }
            }
        """
        file("src/main/swift/Lib.swift") << """
            import Lib1
            class Lib {
                init() {
                    Lib1.thing()
                }
            }
        """

        when:
        run("generateSwiftPmManifest")

        then:
        file("Package.swift").text.contains("""
    dependencies: [
        .package(url: "repo/lib1", .branch("release")),
    ],
""")
        swiftPmBuildSucceeds()

        cleanup:
        lib1Repo?.close()
    }
}
