/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.java.compile.jpms

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.JavaCompileMultiTestRunner
import org.gradle.test.fixtures.maven.MavenModule
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.gradle.util.TextUtil
import org.junit.runner.RunWith

import static org.gradle.test.fixtures.jpms.ModuleJarFixture.autoModuleJar
import static org.gradle.test.fixtures.jpms.ModuleJarFixture.moduleJar
import static org.gradle.test.fixtures.jpms.ModuleJarFixture.traditionalJar

@Requires(TestPrecondition.JDK9_OR_LATER)
@RunWith(JavaCompileMultiTestRunner.class)
abstract class AbstractJavaModuleIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        settingsFile << "rootProject.name = 'consumer'\n"
        buildFile << """
            plugins {
                id 'java-library'
            }
            group = 'org'
            version = '1.0-beta2'

            repositories {
                maven { url '${mavenRepo.uri}' }
            }
        """
        switch (JavaCompileMultiTestRunner.compiler) {
            case JavaCompileMultiTestRunner.Compiler.IN_PROCESS_JDK_COMPILER:
                buildFile << """
                    tasks.withType(JavaCompile) {
                        modularClasspathHandling.inferModulePath.set(true)
                    }
                """
                break
            case JavaCompileMultiTestRunner.Compiler.WORKER_JDK_COMPILER:
                buildFile << """
                    tasks.withType(JavaCompile) {
                        modularClasspathHandling.inferModulePath.set(true)
                        options.fork = true
                    }
                """
                break
            case JavaCompileMultiTestRunner.Compiler.WORKER_COMMAND_LINE_COMPILER:
                def javaHome = TextUtil.escapeString(AvailableJavaHomes.getJdk(JavaVersion.current()).javaHome.absolutePath)
                buildFile << """
                    tasks.withType(JavaCompile) {
                        modularClasspathHandling.inferModulePath.set(true)
                        options.fork = true
                        options.forkOptions.javaHome = file('$javaHome')
                    }
                """
                break
        }
    }

    protected MavenModule publishJavaModule(String name, String moduleInfoStatements = '') {
        mavenRepo.module('org', name, '1.0').mainArtifact(content: moduleJar(name, moduleInfoStatements)).publish()
    }

    protected MavenModule publishJavaLibrary(String name) {
        mavenRepo.module('org', name, '1.0').mainArtifact(content: traditionalJar(name)).publish()
    }

    protected MavenModule publishAutoModule(String name) {
        mavenRepo.module('org', name, '1.0').mainArtifact(content: autoModuleJar(name)).publish()
    }

    protected consumingModuleClass(String... dependencies) {
        file('src/main/java/consumer/MainModule.java').text = """
            package consumer;

            public class MainModule {
                public void run() {
                    ${dependencies.collect { "new ${it}();"}.join('\n')}
                }

                public static void main(String[] args) {
                    new MainModule().run();
                    System.out.println("Module Name: " + MainModule.class.getModule().getName());
                }
            }
        """
    }

    protected consumingModuleInfo(String... statements) {
        file('src/main/java/module-info.java').text = "module consumer { ${statements.collect { it + ';' }.join(' ') } }"
    }
}
