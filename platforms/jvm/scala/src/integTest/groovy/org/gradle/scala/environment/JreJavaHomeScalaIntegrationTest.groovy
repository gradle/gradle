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


import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.ScalaCoverage
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.scala.ScalaCompilationFixture
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

@TargetCoverage({ScalaCoverage.SUPPORTED_BY_JDK})
class JreJavaHomeScalaIntegrationTest extends MultiVersionIntegrationSpec {
    @Requires(UnitTestPreconditions.Windows)
    def "scala compilation works when gradle is started with no java_home defined"() {
        given:
        writeScalaTestSource("src/main/scala");
        file('build.gradle') << """
                    apply plugin:'scala'

                    ${mavenCentralRepository()}

                    dependencies {
                        implementation '${ScalaCompilationFixture.scalaDependency(version)}'
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
                def main(args: Array[String]): Unit = {
                    println("Hello, world!")
                }
            }
        }
        """
    }
}
