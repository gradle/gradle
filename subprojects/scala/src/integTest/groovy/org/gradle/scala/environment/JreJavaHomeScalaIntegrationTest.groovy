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
import org.gradle.integtests.fixtures.ScalaCoverage
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.integtests.fixtures.ZincScalaCompileFixture
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.junit.Rule
import spock.lang.IgnoreIf

@TargetCoverage({ScalaCoverage.DEFAULT})
class JreJavaHomeScalaIntegrationTest extends AbstractIntegrationSpec {

    @Rule public final ZincScalaCompileFixture zincScalaCompileFixture = new ZincScalaCompileFixture(executer, temporaryFolder)

    @IgnoreIf({ AvailableJavaHomes.bestJre == null})
    def "scala java cross compilation works when JAVA_HOME is set to JRE"() {
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

                    ${mavenCentralRepository()}

                    dependencies {
                        implementation 'org.scala-lang:scala-library:2.11.12'
                    }
                    """
        when:
        executer.withJavaHome(jreJavaHome.absolutePath).withTasks("compileScala").run()

        then:
        scalaClassFile("org/test/JavaClazz.class").exists()
        scalaClassFile("org/test/ScalaClazz.class").exists()
    }

    @Requires(UnitTestPreconditions.Windows)
    def "scala compilation works when gradle is started with no java_home defined"() {
        given:
        writeScalaTestSource("src/main/scala");
        file('build.gradle') << """
                    apply plugin:'scala'

                    ${mavenCentralRepository()}

                    dependencies {
                        implementation 'org.scala-lang:scala-library:2.11.12'
                    }
                    """
        def envVars = System.getenv().findAll { !(it.key in ['GRADLE_OPTS', 'JAVA_HOME', 'Path']) }
        envVars.put("Path", "C:\\Windows\\System32")
        when:
        executer.withEnvironmentVars(envVars).withTasks("compileScala").run()
        then:
        scalaClassFile("org/test/ScalaClazz.class").exists()
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
