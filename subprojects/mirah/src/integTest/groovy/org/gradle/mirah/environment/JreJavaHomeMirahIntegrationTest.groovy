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

package org.gradle.mirah.environment

import org.gradle.integtests.fixtures.*
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule
import spock.lang.IgnoreIf
import spock.lang.Unroll

@TargetCoverage({MirahCoverage.DEFAULT})
class JreJavaHomeMirahIntegrationTest extends AbstractIntegrationSpec {

    @Rule public final ForkMirahCompileInDaemonModeFixture forkMirahCompileInDaemonModeFixture = new ForkMirahCompileInDaemonModeFixture(executer, temporaryFolder)

    @IgnoreIf({ AvailableJavaHomes.bestJre == null})
    @Unroll
    def "mirah java cross compilation works in forking mode = #forkMode when JAVA_HOME is set to JRE"() {
        if (GradleContextualExecuter.daemon && !forkMode) {
            // don't load up mirah in process when testing with the daemon as it blows out permgen
            return
        }

        given:
        def jreJavaHome = AvailableJavaHomes.bestJre
        file("src/main/java/org/test/JavaClazz.java") << """
                    package org.test;
                    public class JavaClazz {
                        public static void main(String... args){

                        }
                    }
                    """
        writeMirahTestSource("src/main/mirah")
        file('build.gradle') << """
                    println "Used JRE: ${jreJavaHome.absolutePath.replace(File.separator, '/')}"
                    apply plugin:'mirah'

                    buildscript {
                        repositories {
                            mavenCentral()
                        }
                        
                        dependencies {
                            classpath 'org.mirah:mirah:0.1.4'
                        }
                    }

                    repositories {
                        mavenCentral()
                    }

                    compileMirah {
                        mirahCompileOptions.fork = ${forkMode}
                    }
                    """
        when:
        executer.withEnvironmentVars("JAVA_HOME": jreJavaHome.absolutePath).withTasks("compileMirah").run().output
        then:
        file("build/classes/main/org/test/JavaClazz.class").exists()
        file("build/classes/main/org/test/MirahClazz.class").exists()

        where:
        forkMode << [false, true]
    }

    @Requires(TestPrecondition.WINDOWS)
    def "mirah compilation works when gradle is started with no java_home defined"() {
        given:
        writeMirahTestSource("src/main/mirah");
        file('build.gradle') << """
                    apply plugin:'mirah'

                    buildscript {
                        repositories {
                            mavenCentral()
                        }
                        
                        dependencies {
                            classpath 'org.mirah:mirah:0.1.4'
                        }
                    }

                    repositories {
                        mavenCentral()
                    }
                    """
        def envVars = System.getenv().findAll { !(it.key in ['GRADLE_OPTS', 'JAVA_HOME', 'Path']) }
        envVars.put("Path", "C:\\Windows\\System32")
        when:
        executer.withEnvironmentVars(envVars).withTasks("compileMirah").run()
        then:
        file("build/classes/main/org/test/MirahClazz.class").exists()
    }

    private writeMirahTestSource(String srcDir) {
        file(srcDir, 'org/test/MirahClazz.mirah') << """
        package org.test {
            class MirahClazz
                def self.main(args:String[])
                    puts "Hello, world!"
                end
            end
        }
        """
    }
}
