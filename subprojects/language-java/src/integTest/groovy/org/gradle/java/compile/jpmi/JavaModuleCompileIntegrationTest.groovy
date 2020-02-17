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

package org.gradle.java.compile.jpmi

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class JavaModuleCompileIntegrationTest extends AbstractIntegrationSpec {

    def buildFileA = file('module-a/build.gradle')

    def setup() {
        settingsFile << """
            include('module-a')
        """

        buildFileA << """
            apply plugin: 'java-library'

            tasks.withType(JavaCompile) {
                modulePathHandling = ModulePathHandling.AUTO
            }
        """

        file('module-a/src/main/java/ma/ModuleA.java') << '''
            package mapackage;

            public class ModuleA {}
        '''

        file('src/main/java/root/MainModule.java')  << '''
            package root;

            import mapackage.ModuleA;

            public class MainModule {
                private ModuleA moduleA = new ModuleA();
            }
        '''

        buildFile << """
            apply plugin: 'java-library'

            tasks.withType(JavaCompile) {
                modulePathHandling = ModulePathHandling.AUTO
            }

            dependencies {
                api project(':module-a')
            }
        """
    }

    def "compiles a module using the module path"() {
        given:
        file('module-a/src/main/java/module-info.java') << 'module ma { exports mapackage; }'
        file('src/main/java/module-info.java') << 'module root { requires ma; }'

        when:
        succeeds ':compileJava'

        then:
        javaClassFile('module-info.class').exists()
        javaClassFile('root/MainModule.class').exists()
    }

    def "compiles a non-module using the classpath"() {
        given:
        file('module-a/src/main/java/module-info.java') << 'module ma { }'

        when:
        succeeds ':compileJava'

        then:
        javaClassFile('root/MainModule.class').exists()
    }

    def "fails module compilation for missing requires"() {
        given:
        file('module-a/src/main/java/module-info.java') << 'module ma { exports mapackage; }'
        file('src/main/java/module-info.java') << 'module root { }'

        when:
        fails ':compileJava'

        then:
        failure.assertHasErrorOutput '(package mapackage is declared in module ma, but module root does not read it)'
    }

    def "fails module compilation for missing export"() {
        given:
        file('module-a/src/main/java/module-info.java') << 'module ma { }'
        file('src/main/java/module-info.java') << 'module root { requires ma; }'

        when:
        fails ':compileJava'

        then:
        failure.assertHasErrorOutput '(package mapackage is declared in module ma, which does not export it)'
    }

    def "compiles a module depending on an automatic module"() {
        given:
        buildFileA << """
            tasks.jar {
                manifest.attributes('Automatic-Module-Name': 'ma')
            }
        """
        file('src/main/java/module-info.java') << 'module root { requires ma; }'

        when:
        succeeds ':compileJava', '-Dorg.gradle.java.compile-classpath-packaging=true'

        then:
        javaClassFile('module-info.class').exists()
        javaClassFile('root/MainModule.class').exists()
    }

    def "fails module compilation if module depends on a plain Java library"() {
        given:
        file('src/main/java/module-info.java') << 'module root { }'

        when:
        fails ':compileJava'

        then:
        failure.assertHasErrorOutput '(package mapackage is declared in the unnamed module, but module mapackage does not read it)'
    }

    def "compiles a module depending on a plain Java library when adding access to unnamed module"() {
        given:
        file('src/main/java/module-info.java') << 'module root { }'

        buildFile << """
            tasks.withType(JavaCompile) {
                options.compilerArgs += ['--add-reads', 'root=ALL-UNNAMED']
            }
        """

        when:
        succeeds ':compileJava'

        then:
        javaClassFile('module-info.class').exists()
        javaClassFile('root/MainModule.class').exists()
    }

}
