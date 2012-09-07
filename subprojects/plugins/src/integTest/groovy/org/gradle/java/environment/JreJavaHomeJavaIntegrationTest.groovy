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
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import spock.lang.IgnoreIf
import spock.lang.Unroll

class JreJavaHomeJavaIntegrationTest extends AbstractIntegrationSpec {

    @IgnoreIf({ AvailableJavaHomes.bestJre == null})
    @Unroll
    def "java compilation works in forking mode = #forkMode and useAnt = #useAnt when JAVA_HOME is set to JRE"() {
        given:
        def jreJavaHome = AvailableJavaHomes.bestJre
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

    @Requires(TestPrecondition.WINDOWS)
    def "java compilation works in forking mode = #forkMode and useAnt = #useAnt when gradle is started with no JAVA_HOME defined"() {
        given:
        writeJavaTestSource("src/main/java");
        file('build.gradle') << """
                    apply plugin:'java'
                    compileJava{
                        options.fork = ${forkMode}
                        options.useAnt = ${useAnt}
        }
        """
        def envVars = System.getenv().findAll { it.key != 'JAVA_HOME' || it.key != 'Path'}
        envVars.put("Path", "C:\\Windows\\System32")
        when:
        executer.withEnvironmentVars(envVars).withTasks("compileJava").run()
        then:
        file("build/classes/main/org/test/JavaClazz.class").exists()
        where:
            forkMode << [false, true, false]
            useAnt << [false, false, true]
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