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

package org.gradle.scala.environment

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.IgnoreIf
import spock.lang.Unroll

class JreJavaHomeScalaIntegrationTest extends AbstractIntegrationSpec {

    @IgnoreIf({ AvailableJavaHomes.bestJre == null})
    @Unroll
    def "scala java cross compilation works in forking mode = #forkMode when JAVA_HOME is set to JRE"() {
        given:
        def jreJavaHome = AvailableJavaHomes.bestJre
        file("src/main/scala/org/test/JavaClazz.java") << """
                    package org.test;
                    public class JavaClazz {
                        public static void main(String... args){

                        }
                    }
                    """
        writeScalaTestSource("src/main/scala")
        file('build.gradle') << """
                    println "Used JRE: ${jreJavaHome.absolutePath.replace(File.separator, '/')}"
                    apply plugin:'scala'

                    repositories {
                        mavenCentral()
                    }

                    dependencies {
                        compile 'org.scala-lang:scala-library:2.9.2'
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
    def "scala compilation works when gradle is started with no java_home defined"() {
        given:
        writeScalaTestSource("src/main/scala");
        file('build.gradle') << """
                    apply plugin:'scala'

                    repositories {
                        mavenCentral()
                    }

                    dependencies {
                        compile 'org.scala-lang:scala-library:2.9.2'
                    }
                    """
        def envVars = System.getenv().findAll { !(it.key in ['GRADLE_OPTS', 'JAVA_HOME', 'Path']) }
        envVars.put("Path", "C:\\Windows\\System32")
        when:
        executer.withEnvironmentVars(envVars).withTasks("compileScala").run()
        then:
        file("build/classes/main/org/test/ScalaClazz.class").exists()
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
