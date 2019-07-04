/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.groovy

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue
import spock.lang.Unroll

class GroovyLibraryIntegrationTest extends AbstractIntegrationSpec {

    @Issue("gradle/gradle#9872")
    @Unroll
    def "extension methods should be visible to Groovy library consumers (consumer java lib=#consumerIsJavaLib, producer java library=#producerIsJavaLib, CompileStatic=#cs)"() {
        settingsFile << """
            include 'groovy-lib'
        """
        file("build.gradle") << """
            allprojects {
               apply plugin: "groovy"
               dependencies {
                  implementation(localGroovy())
               }
            }            

            if ($consumerIsJavaLib) { apply plugin: 'java-library' }

            dependencies {
                implementation(project(":groovy-lib"))
            }
            """

        if (producerIsJavaLib) {
            file("groovy-lib/build.gradle") << """
                apply plugin: 'java-library'
            """
        }
        file("groovy-lib/src/main/resources/META-INF/groovy/org.codehaus.groovy.runtime.ExtensionModule") << """
            moduleName=Test module
            moduleVersion=1.0-test
            extensionClasses=support.FrenchStrings
        """
        file("groovy-lib/src/main/groovy/support/FrenchStrings.groovy") << """
            package support
            import groovy.transform.CompileStatic
            
            @CompileStatic
            class FrenchStrings {
                static int getTaille(String self) { self.length() }
            }
        """
        file("src/main/groovy/Consumer.groovy") << """
            import groovy.transform.CompileStatic
            
            ${cs ? '@CompileStatic' : ''}
            void check() {
                assert "foo".taille == 3 
            }
        """

        when:
        if (expectFailure) {
            fails ':compileGroovy'
        } else {
            run ':compileGroovy'
        }

        then:
        if (expectFailure) {
            failure.assertHasCause("Compilation failed; see the compiler error output for details")
        }

        // NONE of these combinations should fail.
        // However the JavaLib + CompileStatic version fails
        // because the Groovy compiler will NOT see the extension
        // module descriptor if we use the java library plugin on
        // the producer, because the "classes" variant is selected.
        // It's only a problem with CompileStatic because it _requires_
        // the descriptor to validate the existence of the methods.
        // but the same problem exists if the producer is, for example,
        // a provider of AST transformations
        where:
        consumerIsJavaLib | producerIsJavaLib | cs    | expectFailure
        false             | false             | false | false
        false             | true              | false | false
        false             | false             | true  | false
        false             | true              | true  | true // this one should pass

        true              | false             | false | false
        true              | true              | false | false
        true              | false             | true  | false
        true              | true              | true  | true // this one should pass
    }

}
