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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.maven.MavenModule
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions

import static org.gradle.test.fixtures.jpms.ModuleJarFixture.autoModuleJar
import static org.gradle.test.fixtures.jpms.ModuleJarFixture.moduleJar
import static org.gradle.test.fixtures.jpms.ModuleJarFixture.traditionalJar

@Requires(UnitTestPreconditions.Jdk9OrLater)
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

    protected TestFile consumingModuleClass(String... dependencies) {
        def file = file('src/main/java/consumer/MainModule.java')
        file.text = """
            package consumer;

            public class MainModule {
                public void run() {
                    ${dependencies.collect { "new ${it}();"}.join('\n')}
                }

                protected String protectedName() {
                    return "protected name";
                }

                public static void main(String[] args) throws Exception {
                    new MainModule().run();
                    System.out.println("Module Name: " + MainModule.class.getModule().getName());
                    System.out.println("Module Version: " + (MainModule.class.getModule().getDescriptor() == null ? "null" : MainModule.class.getModule().getDescriptor().version().get()));
                }
            }
        """
        file
    }

    protected consumingModuleInfo(String... statements) {
        file('src/main/java/module-info.java').text = "module consumer { ${statements.collect { it + ';' }.join(' ') } }"
    }
}
