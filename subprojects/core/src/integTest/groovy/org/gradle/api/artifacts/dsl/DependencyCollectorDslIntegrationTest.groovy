/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.artifacts.dsl

import org.gradle.api.plugins.jvm.PlatformDependencyModifiers
import org.gradle.api.plugins.jvm.TestFixturesDependencyModifiers
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.dsl.GradleDsl
import org.gradle.util.internal.ConfigureUtil

abstract class DependencyCollectorDslIntegrationTest extends AbstractIntegrationSpec {
    static final String FOO_GROUP = "org.example"
    static final String FOO_NAME = "foo"
    static final String FOO_VERSION = "1.0"

    static final String BAZ_GROUP = "com.example"
    static final String BAZ_NAME = "baz"
    static final String BAZ_VERSION = "1.2"

    static final String PVC_GROUP = "net.example"
    static final String PVC_NAME = "provider.convertible"
    static final String PVC_VERSION = "1.5"

    static final String BAR_TXT = "bar.txt"

    static final String MYDEPS_BUNDLE = "mydeps"

    static final String PROJECT_GROUP = ""
    static final String PROJECT_NAME = "testproject"
    static final String PROJECT_VERSION = "1.1"

    String singleString(String group, String name, String version) {
        return renderString(group + ":" + name + ":" + version)
    }

    String multiString(String group, String name, String version) {
        return "module(" + renderString(group) + ", " + renderString(name) + ", " + renderString(version) + ")"
    }

    String namedArgs(String group, String name, String version) {
        return "module(" + makeNamedArgs([group: renderString(group), name: renderString(name), version: renderString(version)], dsl) + ")"
    }

    String versionCatalog(String group, String name) {
        return "libs.$group.$name"
    }

    String versionCatalogBundle(String bundle) {
        return "libs.bundles.$bundle"
    }

    String testFixtures(String expression) {
        return "testFixtures($expression)"
    }

    String platform(String expression) {
        return "platform($expression)"
    }

    String project() {
        return "project()"
    }

    String project(String path) {
        return "project(" + renderString(path) + ")"
    }

    String dependencyObject(String group, String name, String version) {
        return cast("project.dependencies.create(" + singleString(group, name, version) + ")", "ModuleDependency", dsl)
    }

    String files(String path) {
        return "files(${renderString(path)})"
    }

    String fileTree(String path) {
        return "fileTree(${renderString(path)})"
    }

    String providerOf(String expression) {
        return "project.provider { $expression }"
    }

    def setup() {
        createDirs("subproject")
        settingsFile("""
            include "subproject"

            rootProject.name = "${PROJECT_NAME}"
            gradle.rootProject {
                version = "${PROJECT_VERSION}"
            }
        """)
        versionCatalogFile("""
            [libraries]
            org-example-foo = "${FOO_GROUP}:${FOO_NAME}:${FOO_VERSION}"
            com-example-baz = "${BAZ_GROUP}:${BAZ_NAME}:${BAZ_VERSION}"
            net-example-provider-convertible = "${PVC_GROUP}:${PVC_NAME}:${PVC_VERSION}"
            net-example-provider-convertible-nested = "${PVC_GROUP}:${PVC_NAME}:${PVC_VERSION}"

            [bundles]
            ${MYDEPS_BUNDLE} = ["org-example-foo", "com-example-baz"]
        """)
        file(BAR_TXT).text = "bar"
    }

    def "dependency declared using #expression shows up in related configuration"() {
        given:
        file(dsl.fileNameFor("build")).text = """
        ${setupDependencies()}

        dependencies {
            testingCollector(${expression})
        }

        testingCollectorConf.dependencies.forEach {
            println("\${it.group}:\${it.name}:\${it.version}")
        }
        """

        when:
        succeeds("help")

        then:
        outputContains("${FOO_GROUP}:${FOO_NAME}:${FOO_VERSION}")

        where:
        expression << ([
            singleString(FOO_GROUP, FOO_NAME, FOO_VERSION),
            newStringBuilder(singleString(FOO_GROUP, FOO_NAME, FOO_VERSION), dsl),
            multiString(FOO_GROUP, FOO_NAME, FOO_VERSION),
            namedArgs(FOO_GROUP, FOO_NAME, FOO_VERSION),
            versionCatalog(FOO_GROUP, FOO_NAME),
            dependencyObject(FOO_GROUP, FOO_NAME, FOO_VERSION),
            providerOf(dependencyObject(FOO_GROUP, FOO_NAME, FOO_VERSION)),
        ].collectMany {
            [
                it,
                testFixtures(it),
                platform(it),
            ]
        })
    }

    def "ProviderConvertible dependency declared using #expression shows up in related configuration"() {
        given:
        file(dsl.fileNameFor("build")).text = """
        ${setupDependencies()}

        dependencies {
            // This test can be removed if we stop having two different types for Version Catalog accessors
            assert(${versionCatalog(PVC_GROUP, PVC_NAME)} ${instanceOf(dsl)} ProviderConvertible${dsl == GradleDsl.GROOVY ? "" : "<*>"})
            testingCollector(${expression})
        }

        testingCollectorConf.dependencies.forEach {
            println("\${it.group}:\${it.name}:\${it.version}")
        }
        """

        when:
        succeeds("help")

        then:
        outputContains("${PVC_GROUP}:${PVC_NAME}:${PVC_VERSION}")

        where:
        expression << ([
            versionCatalog(PVC_GROUP, PVC_NAME),
        ].collectMany {
            [
                it,
                testFixtures(it),
                platform(it),
            ]
        })
    }

    def "built-in dependency declared using #expression shows up in related configuration"() {
        given:
        file(dsl.fileNameFor("build")).text = """
        ${setupDependencies()}

        dependencies {
            testingCollector(${expression})
        }

        assert(testingCollectorConf.dependencies.iterator().next() == project.dependencies.${expression})
        """
        expect:
        succeeds("help")

        where:
        expression << [
            "gradleApi()",
            "gradleTestKit()",
            "localGroovy()",
        ]
    }

    def "FileCollectionDependency declared using #expression shows up in related configuration"() {
        given:
        file(dsl.fileNameFor("build")).text = """
        ${setupDependencies()}

        dependencies {
            testingCollector($expression)
        }

        println("\${${cast("testingCollectorConf.dependencies.iterator().next()", "FileCollectionDependency", dsl)}.files.singleFile}")
        """

        when:
        succeeds("help")

        then:
        outputContains(BAR_TXT)

        where:
        expression << [
            files(BAR_TXT),
            fileTree(BAR_TXT),
        ]
    }

    def "ProjectDependency declared using #expression shows up in related configuration"() {
        given:
        file(dsl.fileNameFor("build")).text = """
        ${setupDependencies()}

        dependencies {
            testingCollector(${expression})
        }

        var dep = testingCollectorConf.dependencies.iterator().next()
        assert(dep ${instanceOf(dsl)} ProjectDependency)
        assert(${cast("dep", "ProjectDependency", dsl)}.path == ${expectedProjectExpression}.path)
        """

        expect:
        succeeds("help")

        where:
        expression              | expectedProjectExpression
        project()               | "project"
        project(":subproject")  | "project.project(\":subproject\")"
        testFixtures(project()) | "project"
    }

    def "bundles add dependencies that show up in related configuration"() {
        given:
        file(dsl.fileNameFor("build")).text = """
        ${setupDependencies()}

        dependencies {
            testingCollector.bundle(${versionCatalogBundle(MYDEPS_BUNDLE)})
        }

        testingCollectorConf.dependencies.forEach {
            println("\${it.group}:\${it.name}:\${it.version}")
        }
        """

        when:
        succeeds("help")

        then:
        outputContains("${FOO_GROUP}:${FOO_NAME}:${FOO_VERSION}")
        outputContains("${BAZ_GROUP}:${BAZ_NAME}:${BAZ_VERSION}")
    }

    def "dependency configuration actions are applied for #expression"() {
        given:
        file(dsl.fileNameFor("build")).text = """
        ${setupDependencies()}

        dependencies {
            testingCollector(${expression}) {
                because("the action must be tested")
                ${testExcludes ? "exclude(${makeNamedArgs([group: renderString(BAZ_GROUP), module: renderString(BAZ_NAME)], dsl)})" : ""}
            }
        }

        testingCollectorConf.dependencies.forEach {
            println("\${it.reason}")
            if (${testExcludes}) {
                ${cast("it", "ModuleDependency", dsl)}.excludeRules.forEach {
                    println("\${it.group}:\${it.module}")
                }
            }
        }
        """

        when:
        succeeds("help")

        then:
        outputContains("the action must be tested")
        if (testExcludes) {
            outputContains("${BAZ_GROUP}:${BAZ_NAME}")
        }

        where:
        // Keeping it simpler here, since modifiers are all the same, and multi-string + named args are also the same
        expression                                                     | testExcludes
        singleString(FOO_GROUP, FOO_NAME, FOO_VERSION)                 | true
        multiString(FOO_GROUP, FOO_NAME, FOO_VERSION)                  | true
        versionCatalog(FOO_GROUP, FOO_NAME)                            | true
        platform(singleString(FOO_GROUP, FOO_NAME, FOO_VERSION))       | true
        platform(versionCatalog(FOO_GROUP, FOO_NAME))                  | true
        dependencyObject(FOO_GROUP, FOO_NAME, FOO_VERSION)             | true
        providerOf(dependencyObject(FOO_GROUP, FOO_NAME, FOO_VERSION)) | true
        project()                                                      | true
        "gradleApi()"                                                  | false
        files(BAR_TXT)                                                 | false
        fileTree(BAR_TXT)                                              | false
    }

    def "dependency configuration actions are applied for version catalog bundles"() {
        given:
        file(dsl.fileNameFor("build")).text = """
        ${setupDependencies()}

        dependencies {
            testingCollector.bundle(${versionCatalogBundle(MYDEPS_BUNDLE)}) {
                because("the action must be tested")
                exclude(${makeNamedArgs([group: renderString(BAZ_GROUP), module: renderString(BAZ_NAME)], dsl)})
            }
        }

        testingCollectorConf.dependencies.forEach {
            println("\${it.reason}")
            ${cast("it", "ModuleDependency", dsl)}.excludeRules.forEach {
                println("\${it.group}:\${it.module}")
            }
        }
        """

        when:
        succeeds("help")

        then:
        outputContains("""the action must be tested
${BAZ_GROUP}:${BAZ_NAME}
the action must be tested
${BAZ_GROUP}:${BAZ_NAME}
""")
    }

    def "dependency constraint declared using #expression shows up in related configuration"() {
        given:
        file(dsl.fileNameFor("build")).text = """
        ${setupDependencies()}

        dependencies {
            testingCollector(constraint(${expression}))
        }

        testingCollectorConf.dependencyConstraints.forEach {
            println("\${it.group}:\${it.name}:\${it.version}")
        }
        """

        when:
        succeeds("help")

        then:
        outputContains(expectedDependencyCoordinates)

        where:
        expression                                                            | expectedDependencyCoordinates
        singleString(FOO_GROUP, FOO_NAME, FOO_VERSION)                        | "${FOO_GROUP}:${FOO_NAME}:${FOO_VERSION}"
        newStringBuilder(singleString(FOO_GROUP, FOO_NAME, FOO_VERSION), dsl) | "${FOO_GROUP}:${FOO_NAME}:${FOO_VERSION}"
        versionCatalog(FOO_GROUP, FOO_NAME)                                   | "${FOO_GROUP}:${FOO_NAME}:${FOO_VERSION}"
        project()                                                             | "${PROJECT_GROUP}:${PROJECT_NAME}:${PROJECT_VERSION}"
    }

    abstract GradleDsl getDsl();

    abstract String setupDependencies();
}

class DependencyCollectorGroovyDslIntegrationTest extends DependencyCollectorDslIntegrationTest {
    GradleDsl getDsl() {
        return GradleDsl.GROOVY
    }

    @Override
    String setupDependencies() {
        return """
            abstract class MyDependencies implements Dependencies, ${TestFixturesDependencyModifiers.class.name}, ${PlatformDependencyModifiers.class.name}, ${GradleDependencies.class.name} {
                abstract DependencyCollector getTestingCollector()

                void call(Closure closure) {
                    ${ConfigureUtil.class.name}.configure(closure, this)
                }
            }

            def dependencies = objects.newInstance(MyDependencies)

            def testingCollectorConf = configurations.dependencyScope("testingCollector").get()
            testingCollectorConf.fromDependencyCollector(dependencies.testingCollector)
        """
    }

    private static final String ERROR_MESSAGE_PROVIDER = "Providers of type 'java.lang.String' are not supported. Only Provider<Dependency> and Provider<DependencyConstraint> are supported. Try using the Provider#map method to convert to a supported type.";

    def "cannot add non-Dependency providers to the dependency collector (fails at runtime)"() {
        given:
        file(dsl.fileNameFor("build")).text = """
        ${setupDependencies()}

        dependencies {
            testingCollector(${providerOf(singleString(FOO_GROUP, FOO_NAME, FOO_VERSION))})
        }
        """

        when:
        fails("dependencies")

        then:
        failureCauseContains(ERROR_MESSAGE_PROVIDER)
    }
}

class DependencyCollectorKotlinDslIntegrationTest extends DependencyCollectorDslIntegrationTest {
    GradleDsl getDsl() {
        return GradleDsl.KOTLIN
    }

    @Override
    String setupDependencies() {
        return """
            abstract class MyDependencies : Dependencies, ${TestFixturesDependencyModifiers.class.name}, ${PlatformDependencyModifiers.class.name}, ${GradleDependencies.class.name} {
                abstract val testingCollector: DependencyCollector

                operator fun invoke(closure: MyDependencies.() -> Unit) {
                    closure(this)
                }
            }

            val dependencies = objects.newInstance(MyDependencies::class.java)

            val testingCollectorConf = configurations.dependencyScope("testingCollector").get()
            testingCollectorConf.fromDependencyCollector(dependencies.testingCollector)
        """
    }

    def "cannot add non-Dependency providers to the dependency collector (fails at compile time)"() {
        given:
        file(dsl.fileNameFor("build")).text = """
        ${setupDependencies()}

        dependencies {
            testingCollector(${providerOf(singleString(FOO_GROUP, FOO_NAME, FOO_VERSION))})
        }
        """

        when:
        fails("dependencies")

        then:
        result.assertHasErrorOutput("""None of the following functions can be called with the arguments supplied:${' '}
public operator fun DependencyCollector.invoke""") // Don't care what the other options are, just that it's the right name
    }
}
