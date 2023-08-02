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

package org.gradle.java.environment

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import spock.lang.IgnoreIf

class JreJavaHomeJavaIntegrationTest extends AbstractIntegrationSpec {

    @IgnoreIf({ AvailableJavaHomes.bestJre == null })
    def "java compilation works in forking mode = #forkMode when JAVA_HOME is set to JRE"() {
        given:
        def jreJavaHome = AvailableJavaHomes.bestJre
        writeJavaTestSource("src/main/java");
        file('build.gradle') << """
        println "Used JRE: ${jreJavaHome.absolutePath.replace(File.separator, '/')}"
        apply plugin:'java'
        compileJava {
            options.fork = ${forkMode}
        }
        """
        when:
        executer.withJavaHome(jreJavaHome.absolutePath).withTasks("compileJava").run().output
        then:
        javaClassFile("org/test/JavaClazz.class").exists()

        where:
        forkMode << [true, false]
    }

    @Requires(UnitTestPreconditions.Windows)
    def "java compilation works in forking mode = #forkMode when gradle is started with no JAVA_HOME defined"() {
        given:
        writeJavaTestSource("src/main/java");
        file('build.gradle') << """
        apply plugin:'java'
        compileJava {
            options.fork = ${forkMode}
        }
        """
        def envVars = System.getenv().findAll { !(it.key in ['GRADLE_OPTS', 'JAVA_HOME', 'Path']) }
        envVars.put("Path", "C:\\Windows\\System32")
        when:
        executer.withEnvironmentVars(envVars).withTasks("compileJava").run()
        then:
        javaClassFile("org/test/JavaClazz.class").exists()
        where:
        forkMode << [true, false]
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
}
