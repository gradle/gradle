/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache

class ConfigurationBuildDependenciesIntegrationTest extends AbstractHttpDependencyResolutionTest {
    def setup() {
        createDirs("child")
        settingsFile << "include 'child'"
        buildFile << """
            allprojects {
                configurations {
                    compile
                    create('default').extendsFrom compile
                }
            }
            task useCompileConfiguration {
                inputs.files configurations.compile
                outputs.file file('output.txt')
                doLast { }
            }
"""
    }

    def "configuration views used as task input build required files"() {
        buildFile << '''
            allprojects {
                task lib
                task jar
            }
            dependencies {
                compile project(':child')
                compile files('main-lib.jar') { builtBy lib }
            }
            project(':child') {
                artifacts {
                    compile file: file('child.jar'), builtBy: jar
                }
                dependencies {
                    compile files('child-lib.jar') { builtBy lib }
                }
            }
            task direct { inputs.files configurations.compile }
            task fileCollection { inputs.files configurations.compile.fileCollection { true } }
            task ownDependencies { dependsOn configurations.compile.dependencies }
            task allDependencies { dependsOn configurations.compile.allDependencies }
            task incomingFiles { inputs.files configurations.compile.incoming.files }
            task incomingDependencies { dependsOn configurations.compile.incoming.dependencies }
            task copy { inputs.files configurations.compile.copy() }
            task filteredTree { inputs.files configurations.compile.asFileTree.matching { true } }
'''

        when:
        executer.expectDocumentedDeprecationWarning("The Configuration.fileCollection(Closure) method has been deprecated. This is scheduled to be removed in Gradle 9.0. Use Configuration.getIncoming().artifactView(Action) with a componentFilter instead. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_8.html#deprecate_filtered_configuration_file_and_filecollection_methods")
        run taskName

        then:
        executed ":lib", ":child:jar", ":child:lib", ":$taskName"

        where:
        taskName               | _
        "direct"               | _
        "fileCollection"       | _
        "ownDependencies"      | _
        "allDependencies"      | _
        "incomingFiles"        | _
        "incomingDependencies" | _
        "copy"                 | _
        "filteredTree"         | _
    }

    def "builds correct artifacts when there is a project cycle in dependency graph - fluid: #fluid"() {
        makeFluid(fluid)

        // A graph from root compile -> child default -> root default, so not an actual cycle here
        // Graph includes artifact and file dependencies on each node, should build all of them
        buildFile << """
            allprojects {
                task jar
                task lib
                artifacts {
                    compile file: file("\${project.name}.jar"), builtBy: jar
                }
                dependencies {
                    compile files("\${project.name}-lib.jar") { builtBy lib }
                }
            }
            dependencies {
                compile project(':child')
            }
            project(':child') {
                dependencies {
                    compile project(':') // references 'default' not 'compile' so there is not _actually_ a cycle here
                }
            }
"""

        when:
        run("useCompileConfiguration")

        then:
        executed ":jar", ":lib", ":child:jar", ":child:lib", ":useCompileConfiguration"

        where:
        fluid << [true, false]
    }

    def "builds correct artifacts when there is a cycle in dependency graph - fluid: #fluid"() {
        makeFluid(fluid)

        // A graph from root compile -> child default -> root compile
        // Graph includes artifact and file dependencies on each node, should build all of them
        buildFile << """
            allprojects {
                task jar
                task lib
                configurations {
                    conf
                }
                artifacts {
                    conf file: file("\${project.name}.jar"), builtBy: jar
                }
                dependencies {
                    conf files("\${project.name}-lib.jar") { builtBy lib }
                }
            }
            dependencies {
                compile project(path: ':child', configuration: 'conf')
                conf project(path: ':child', configuration: 'conf')
            }
            project(':child') {
                dependencies {
                    conf project(path: ':', configuration: 'conf')
                }
            }
"""

        when:
        run("useCompileConfiguration")

        then:
        executed ":jar", ":lib", ":child:jar", ":child:lib", ":useCompileConfiguration"

        where:
        fluid << [true, false]
    }

    def "reports failure to calculate build dependencies when artifact build dependencies cannot be queried - fluid: #fluid"() {
        makeFluid(fluid)
        buildFile << """
            dependencies {
                compile project(':child')
            }
            project(':child') {
                artifacts {
                    compile file: file('thing.txt'), builtBy: { throw new RuntimeException('broken') }
                }
            }
"""

        expect:
        executer.withArgument("--dry-run")
        fails("useCompileConfiguration")
        failure.assertHasDescription("Could not determine the dependencies of task ':useCompileConfiguration'.")
        failure.assertHasCause('broken')

        fails("useCompileConfiguration")
        failure.assertHasDescription("Could not determine the dependencies of task ':useCompileConfiguration'.")
        failure.assertHasCause('broken')

        where:
        fluid << [true, false]
    }

    def "reports failure to calculate build dependencies when file dependency cannot be resolved - fluid: #fluid"() {
        makeFluid(fluid)
        buildFile << """
            dependencies {
                compile project(':child')
            }
            project(':child') {
                dependencies {
                    compile files({ throw new RuntimeException('broken') })
                }
            }
"""

        expect:
        executer.withArgument("--dry-run")
        fails("useCompileConfiguration")
        failure.assertHasDescription("Could not determine the dependencies of task ':useCompileConfiguration'.")
        failure.assertHasCause('broken')

        fails("useCompileConfiguration")
        failure.assertHasDescription("Could not determine the dependencies of task ':useCompileConfiguration'.")
        failure.assertHasCause('broken')

        where:
        fluid << [true, false]
    }

    def "reports failure to calculate build dependencies when local configuration cannot be selected - fluid: #fluid"() {
        makeFluid(fluid)
        buildFile << """
            dependencies {
                compile project(':child')
            }
            project(':child') {
                configurations.default.canBeConsumed = false
            }
"""

        expect:
        executer.withArgument("--dry-run")
        fails("useCompileConfiguration")
        failure.assertHasDescription("Could not determine the dependencies of task ':useCompileConfiguration'.")
        failure.assertHasCause("Could not resolve all dependencies for configuration ':compile'.")
        failure.assertHasCause("""Unable to find a matching variant of project :child:
  - No variants exist.""")

        fails("useCompileConfiguration")
        failure.assertHasDescription("Could not determine the dependencies of task ':useCompileConfiguration'.")
        failure.assertHasCause("Could not resolve all dependencies for configuration ':compile'.")
        failure.assertHasCause("""Unable to find a matching variant of project :child:
  - No variants exist.""")

        where:
        fluid << [true, false]
    }

    def "reports failure to calculate build dependencies when local variant cannot be selected - fluid: #fluid"() {
        makeFluid(fluid)
        buildFile << """
            dependencies {
                compile project(':child')
            }
            project(':child') {
                configurations.default.outgoing.variants {
                    v1 { }
                    v2 { }
                }
            }
"""

        expect:
        executer.withArgument("--dry-run")
        fails("useCompileConfiguration")
        failure.assertHasDescription("Could not determine the dependencies of task ':useCompileConfiguration'.")
        failure.assertHasCause("Could not resolve all dependencies for configuration ':compile'.")
        failure.assertHasCause("More than one variant of project :child matches the consumer attributes:")

        fails("useCompileConfiguration")
        failure.assertHasDescription("Could not determine the dependencies of task ':useCompileConfiguration'.")
        failure.assertHasCause("Could not resolve all dependencies for configuration ':compile'.")
        failure.assertHasCause("More than one variant of project :child matches the consumer attributes:")

        where:
        fluid << [true, false]
    }

    def "reports failure to find build dependencies for broken external component when using fluid dependencies"() {
        def module = mavenHttpRepo.module("test", "test", "1.0").publish()
        makeFluid(true)
        buildFile << """
            allprojects {
                repositories {
                    maven { url '$mavenHttpRepo.uri' }
                }
            }

            dependencies {
                compile project(':child')
            }
            project(':child') {
                dependencies {
                    compile 'test:test:1.0'
                }
            }
"""

        expect:
        module.pom.expectGetBroken()

        executer.withArgument("--dry-run")
        fails("useCompileConfiguration")
        failure.assertHasDescription("Could not determine the dependencies of task ':useCompileConfiguration'.")
        failure.assertHasCause("Could not resolve all dependencies for configuration ':compile'.")
        failure.assertHasCause("Could not resolve test:test:1.0.")
        failure.assertHasCause("Could not get resource '${module.pom.uri}'")

        module.pom.expectGetBroken()
        fails("useCompileConfiguration")
        failure.assertHasDescription("Could not determine the dependencies of task ':useCompileConfiguration'.")
        failure.assertHasCause("Could not resolve all dependencies for configuration ':compile'.")
        failure.assertHasCause("Could not resolve test:test:1.0.")
        failure.assertHasCause("Could not get resource '${module.pom.uri}'")
    }

    @ToBeFixedForConfigurationCache
    def "does not download anything when task dependencies are calculated for configuration that is used as a task input"() {
        def module = mavenHttpRepo.module("test", "test", "1.0").publish()
        buildFile << """
            allprojects {
                repositories {
                    maven { url '$mavenHttpRepo.uri' }
                }
            }

            dependencies {
                compile project(':child')
            }
            project(':child') {
                task jar {
                    outputs.files file('thing.jar')
                }
                artifacts {
                    compile file: jar.outputs.files.singleFile, builtBy: jar
                }
                dependencies {
                    compile 'test:test:1.0'
                }
            }
"""

        when:
        executer.withArgument("--dry-run")
        run 'useCompileConfiguration'

        then:
        server.resetExpectations()

        when:
        // Expect downloads when task executed
        module.pom.expectGet()
        module.artifact.expectGet()
        run 'useCompileConfiguration'

        then:
        executed ":child:jar", ":useCompileConfiguration"
    }

    @ToBeFixedForConfigurationCache
    def "does not download artifacts when task dependencies are calculated for configuration that is used as a task input when using fluid dependencies"() {
        def module = mavenHttpRepo.module("test", "test", "1.0").publish()
        makeFluid(true)
        buildFile << """
            allprojects {
                repositories {
                    maven { url '$mavenHttpRepo.uri' }
                }
            }

            dependencies {
                compile project(':child')
            }
            project(':child') {
                task jar {
                    outputs.files file('thing.jar')
                }
                artifacts {
                    compile file: jar.outputs.files.singleFile, builtBy: jar
                }
                dependencies {
                    compile 'test:test:1.0'
                }
            }
"""

        when:
        module.pom.expectGet()
        executer.withArgument("--dry-run")
        run 'useCompileConfiguration'

        then:
        server.resetExpectations()

        when:
        // Expect downloads when task executed
        module.artifact.expectGet()
        run 'useCompileConfiguration'

        then:
        executed ":child:jar", ":useCompileConfiguration"
    }

    void makeFluid(boolean fluid) {
        if (fluid) {
            buildFile << """
allprojects { configurations.all { resolutionStrategy.assumeFluidDependencies() } }
"""
        }
    }
}
