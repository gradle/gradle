/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.groovy.environment

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import spock.lang.IgnoreIf

class JreJavaHomeGroovyIntegrationTest extends AbstractIntegrationSpec {

    @IgnoreIf({ AvailableJavaHomes.bestJre == null})
    def "groovy java cross compilation works in forking mode = #forkMode when JAVA_HOME is set to JRE"() {
        given:
        def jreJavaHome = AvailableJavaHomes.bestJre
        writeJavaTestSource("src/main/groovy")
        writeGroovyTestSource("src/main/groovy")
        file('build.gradle') << """
                println "Used JRE: ${jreJavaHome.absolutePath.replace(File.separator, '/')}"
                apply plugin:'groovy'
                dependencies{
                    implementation localGroovy()
                }
                compileGroovy{
                    options.fork = ${forkMode}
                }
                """
        when:
        executer.withEnvironmentVars("JAVA_HOME": jreJavaHome.absolutePath).withTasks("compileGroovy").run().output
        then:
        groovyClassFile("org/test/JavaClazz.class").exists()
        groovyClassFile("org/test/GroovyClazz.class").exists()

        where:
        forkMode << [true, false]
    }

    @Requires(UnitTestPreconditions.Windows)
    def "groovy compiler works when gradle is started with no JAVA_HOME defined in forking mode = #forkMode"() {
        given:
        writeJavaTestSource("src/main/groovy")
        writeGroovyTestSource("src/main/groovy")
        file('build.gradle') << """
            apply plugin:'groovy'
            dependencies {
                implementation localGroovy()
            }
            compileGroovy {
                options.fork = ${forkMode}
            }
            """
        when:
        def envVars = System.getenv().findAll { !(it.key in ['GRADLE_OPTS', 'JAVA_HOME', 'Path']) }
        envVars.put("Path", "C:\\Windows\\System32")
        executer.withEnvironmentVars(envVars).withTasks("compileGroovy").run()

        then:
        groovyClassFile("org/test/JavaClazz.class").exists()
        groovyClassFile("org/test/GroovyClazz.class").exists()

        where:
        forkMode << [true, false]
    }

    private writeJavaTestSource(String srcDir, String clazzName = "JavaClazz") {
        file(srcDir, "org/test/${clazzName}.java") << """
            package org.test;
            public class ${clazzName} {
                public static void main(String... args){

                }
            }
            """
    }

    private writeGroovyTestSource(String srcDir) {
        file(srcDir, 'org/test/GroovyClazz.groovy') << """
            package org.test
            class GroovyClazz{
                def property = "a property"
            }
            """
    }

}
