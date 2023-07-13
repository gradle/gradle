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

package org.gradle.java.compile

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.CompiledLanguage
import org.gradle.integtests.fixtures.FeaturePreviewsFixture
import org.gradle.integtests.fixtures.jvm.JavaToolchainFixture
import org.gradle.internal.jvm.Jvm

abstract class AbstractIncrementalCompileIntegrationTest extends AbstractIntegrationSpec implements IncrementalCompileMultiProjectTestFixture, JavaToolchainFixture {
    abstract CompiledLanguage getLanguage()

    def setup() {
        if (language == CompiledLanguage.GROOVY) {
            FeaturePreviewsFixture.enableGroovyCompilationAvoidance(settingsFile)
        }
    }

    def "recompiles source when properties change"() {
        given:
        file("src/main/${language.name}/Test.${language.name}") << 'public class Test{}'
        buildFile << """
            apply plugin: '${language.name}'
            java.toolchain.languageVersion = JavaLanguageVersion.of(11)
            java.sourceCompatibility = '1.7'
            ${language.compileTaskName}.options.debug = true
            ${language.compileTaskName}.options.incremental = true
            ${language.projectGroovyDependencies()}
        """.stripIndent()

        when:
        withInstallations(Jvm.current(), AvailableJavaHomes.jdk11).succeeds ":${language.compileTaskName}"

        then:
        executedAndNotSkipped ":${language.compileTaskName}"

        when:
        buildFile << 'java.sourceCompatibility = 1.8\n'
        withInstallations(Jvm.current(), AvailableJavaHomes.jdk11).succeeds ":${language.compileTaskName}"

        then:
        executedAndNotSkipped ":${language.compileTaskName}"

        when:
        buildFile << "${language.compileTaskName}.options.debug = false\n"
        withInstallations(Jvm.current(), AvailableJavaHomes.jdk11).succeeds ":${language.compileTaskName}"

        then:
        executedAndNotSkipped ":${language.compileTaskName}"

        when:
        withInstallations(Jvm.current(), AvailableJavaHomes.jdk11).succeeds ":${language.compileTaskName}"

        then:
        skipped ":${language.compileTaskName}"
    }

    def "recompiles dependent classes"() {
        given:
        file("src/main/${language.name}/IPerson.${language.name}") << basicInterface
        file("src/main/${language.name}/Person.${language.name}") << classImplementingBasicInterface
        buildFile << """
            apply plugin: '${language.name}'
            ${language.compileTaskName}.options.incremental = true
            ${language.projectGroovyDependencies()}
"""

        expect:
        succeeds 'classes'

        when: 'update interface, compile should fail'
        file("src/main/${language.name}/IPerson.${language.name}").text = extendedInterface

        then:
        def failure = fails 'classes'
        failure.assertHasDescription "Execution failed for task ':${language.compileTaskName}'."
    }

    def "recompiles dependent classes across project boundaries"() {
        given:
        file("lib/src/main/${language.name}/IPerson.${language.name}") << basicInterface
        file("app/src/main/${language.name}/Person.${language.name}") << classImplementingBasicInterface
        settingsFile << 'include "lib", "app"'
        buildFile << """
            subprojects {
                apply plugin: '${language.name}'
                ${language.compileTaskName}.options.incremental = true
            }
            project(':app') {
                dependencies {
                    implementation project(':lib')
                }
            }
            ${language.projectGroovyDependencies("subprojects")}
        """.stripIndent()

        expect:
        succeeds 'app:classes'

        when: 'update interface, compile should fail'
        file("lib/src/main/${language.name}/IPerson.${language.name}").text = extendedInterface

        then:
        def failure = fails 'app:classes'
        failure.assertHasDescription "Execution failed for task ':app:${language.compileTaskName}'."
    }

    def "task outcome is UP-TO-DATE when no recompilation necessary"() {
        given:
        libraryAppProjectWithIncrementalCompilation(language)

        when:
        succeeds getAppCompileTask(language)

        then:
        executedAndNotSkipped getAppCompileTask(language)

        when:
        writeUnusedLibraryClass(language)

        then:
        executer.withArgument('-i')
        succeeds getAppCompileTask(language)

        and:
        outputContains "None of the classes needs to be compiled!"
        outputContains "${getAppCompileTask(language)} UP-TO-DATE"
        executedAndNotSkipped(getLibraryCompileTask(language))
    }

    def "does not recompile when only compileOptions.incremental property changes from #from to #to"() {
        given:
        libraryAppProjectWithIncrementalCompilation(language)

        when:
        buildFile << """
            subprojects {
                tasks.${language.compileTaskName}.options.incremental = $from
            }
        """
        run getAppCompileTask(language)

        then:
        executedAndNotSkipped getLibraryCompileTask(language)
        executedAndNotSkipped getAppCompileTask(language)

        when:
        buildFile << """
            subprojects {
                tasks.compileJava.options.incremental = $to
            }
        """
        run getAppCompileTask(language)

        then:
        skipped getLibraryCompileTask(language)
        skipped getAppCompileTask(language)

        where:
        from  | to
        true  | false
        false | true
    }

    def "removes stale class file when file moves in hierarchy"() {
        given:
        file("src/main/${language.name}/IPerson.${language.name}") << basicInterface
        buildFile << """
            apply plugin: '${language.name}'
            ${language.compileTaskName}.options.incremental = true
            ${language.projectGroovyDependencies()}
"""

        when:
        succeeds 'classes'

        then:
        executedAndNotSkipped ":${language.compileTaskName}"
        file("build/classes/${language.name}/main/IPerson.class").exists()
        !file("build/classes/${language.name}/main/some/package/IPerson.class").exists()

        when:
        file("src/main/${language.name}/some/loc/IPerson.${language.name}") << """
            package some.loc;
        """ << basicInterface
        assert file("src/main/${language.name}/IPerson.${language.name}").delete()

        and:
        succeeds 'assemble'

        then:
        executedAndNotSkipped ":${language.compileTaskName}"
        !file("build/classes/${language.name}/main/IPerson.class").exists()
        file("build/classes/${language.name}/main/some/loc/IPerson.class").exists()
    }

    private static String getBasicInterface() {
        '''
            interface IPerson {
                String getName();
            }
        '''.stripIndent()
    }

    private static String getExtendedInterface() {
        '''
            interface IPerson {
                String getName();
                String getAddress();
            }
        '''.stripIndent()
    }

    private static String getClassImplementingBasicInterface() {
        '''
            class Person implements IPerson {
                public String getName() {
                    return "name";
                }
            }
        '''.stripIndent()
    }
}
