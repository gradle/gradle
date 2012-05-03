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

package org.gradle.integtests.environment

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import spock.lang.IgnoreIf
import spock.lang.Unroll
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

/**
 * This test is used to check the correct behaviour of gradle when java home is pointing to a JRE.
 */
class JreJavaHomeIntegratonTest extends AbstractIntegrationSpec {

    @IgnoreIf({ AvailableJavaHomes.bestJreAlternative == null})
    @Unroll
    def "java compilation works in forking mode = #forkMode and useAnt = #useAnt when JAVA_HOME is set to JRE"() {
        given:
        def jreJavaHome = AvailableJavaHomes.bestJreAlternative
        writeJavaTestSource("src/main/java");
        file('build.gradle') << """
        println "Used JRE: ${jreJavaHome.absolutePath.replace(File.separator, '/')}"
        apply plugin:'java'
        compileJava{
            options.fork = ${forkMode}
            options.useAnt = ${useAnt}
        }
        """
        when:
        executer.withEnvironmentVars("JAVA_HOME": jreJavaHome.absolutePath).withTasks("compileJava").run().output
        then:
        file("build/classes/main/org/test/JavaClazz.class").exists()

        where:
        forkMode << [false, true, false]
        useAnt << [false, false, true]
    }

    @IgnoreIf({ AvailableJavaHomes.bestJreAlternative == null})
    @Unroll
    def "groovy java cross compilation works in forking mode = #forkMode and useAnt = #useAnt when JAVA_HOME is set to JRE"() {
        given:
        def jreJavaHome = AvailableJavaHomes.bestJreAlternative
        writeJavaTestSource("src/main/groovy")
        writeGroovyTestSource("src/main/groovy")
        file('build.gradle') << """
            println "Used JRE: ${jreJavaHome.absolutePath.replace(File.separator, '/')}"
            apply plugin:'groovy'
            dependencies{
                groovy localGroovy()
            }
            compileGroovy{
                options.fork = ${forkMode}
                options.useAnt = ${useAnt}
                groovyOptions.useAnt = ${useAnt}
            }
            """
        when:
        executer.withEnvironmentVars("JAVA_HOME": jreJavaHome.absolutePath).withTasks("compileGroovy").run().output
        then:
        file("build/classes/main/org/test/JavaClazz.class").exists()
        file("build/classes/main/org/test/GroovyClazz.class").exists()

        where:
        forkMode << [false, true, false]
        useAnt << [false, false, true]
    }

    @IgnoreIf({ AvailableJavaHomes.bestJreAlternative == null})
    @Unroll
    def "scala java cross compilation works in forking mode = #forkMode when JAVA_HOME is set to JRE"() {
        given:
        def jreJavaHome = AvailableJavaHomes.bestJreAlternative
        writeJavaTestSource("src/main/scala")
        writeScalaTestSource("src/main/scala")
        file('build.gradle') << """
                println "Used JRE: ${jreJavaHome.absolutePath.replace(File.separator, '/')}"
                apply plugin:'scala'

                repositories {
                    mavenCentral()
                }

                dependencies {
                    scalaTools 'org.scala-lang:scala-compiler:2.8.1'
                    scalaTools 'org.scala-lang:scala-library:2.8.1'
                    compile    'org.scala-lang:scala-library:2.8.1'
                }

                compileScala{
                    options.fork = ${forkMode}
                }
                """
        when:
        executer.withEnvironmentVars("JAVA_HOME": jreJavaHome.absolutePath).withTasks("compileScala").run().output
        then:
        file("build/classes/main/org/test/JavaClazz.class").exists()
        file("build/classes/main/org/test/ScalaClazz.class").exists()

        where:
        forkMode << [false, true]
    }

    @Requires(TestPrecondition.WINDOWS)
    def "java compilation works when gradle is started with no java_home defined"() {
        given:
        writeJavaTestSource("src/main/java");
        file('build.gradle') << """
                apply plugin:'java'
                """
        def envVars = System.getenv().findAll { it.key != 'JAVA_HOME' || it.key != 'Path'}
        envVars.put("Path", "C:\\Windows\\System32")
        when:
        executer.withEnvironmentVars(envVars).withTasks("compileJava").run()
        then:
        file("build/classes/main/org/test/JavaClazz.class").exists()
    }


    private writeJavaTestSource(String srcDir) {
        file(srcDir, 'org/test/JavaClazz.java') << """
            package org.test;
            public class JavaClazz {
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

    private writeScalaTestSource(String srcDir) {
        file(srcDir, 'org/test/ScalaClazz.scala') << """
        package org.test{
            object ScalaClazz {
                def main(args: Array[String]) {
                    println("Hello, world!")
                }
            }
        }
        """
    }
}
