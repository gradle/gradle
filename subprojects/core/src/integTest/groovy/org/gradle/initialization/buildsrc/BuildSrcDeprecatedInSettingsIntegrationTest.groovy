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

package org.gradle.initialization.buildsrc

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class BuildSrcDeprecatedInSettingsIntegrationTest extends AbstractIntegrationSpec {

    def "Using buildSrc classes in settings is deprecated"() {
        file('buildSrc/build.gradle') << """
            apply plugin: 'groovy'
            
            repositories {
                mavenCentral()
            }

            dependencies {  
                implementation 'org.apache.commons:commons-math3:3.6.1'                
            }
        """

        file('buildSrc/src/main/java/org/acme/build/SomeBuildSrcClass.java') << """
        package org.acme.build;
        
        public class SomeBuildSrcClass {
            public static void foo(String foo){
                System.out.println(foo);
            }
        }
        """
        file('buildSrc/src/main/java/org/acme/build/SomeOtherBuildSrcClass.java') << """
        package org.acme.build;
        
        public class SomeOtherBuildSrcClass {
            public static void foo(String foo){
                System.out.println(foo);
            }
        }
        """
        settingsFile << """
            buildscript {
                repositories {
                    mavenCentral()
                }
                
                dependencies {  
                    classpath 'org.apache.commons:commons-lang3:3.9'                
                }
            }

            org.acme.build.SomeBuildSrcClass.foo("from settings.gradle")
            
            org.apache.commons.math3.util.FastMath.nextUp(0.2)
            
            org.apache.commons.lang3.StringUtils.capitalize("bar")
        """

        buildFile << """
            org.acme.build.SomeOtherBuildSrcClass.foo("from build.gradle")
        """
        when:
        executer.expectDeprecationWarnings(1)
        succeeds("tasks")
        then:
        outputContains("Access to the buildSrc project and its dependencies in settings scripts has been deprecated.")
    }

    def "Using buildscript classes in settings is not deprecated"() {
        when:
        file('buildSrc/build.gradle') << """
            apply plugin: 'groovy'
            
            repositories {
                mavenCentral()
            }

            dependencies {  
                implementation 'org.apache.commons:commons-math3:3.6.1'                
            }
        """

        file('buildSrc/src/main/java/org/acme/build/SomeBuildSrcClass.java') << """
        package org.acme.build;
        
        public class SomeBuildSrcClass {
            public static void foo(String foo){
                System.out.println(foo);
            }
        }
        """
        file('buildSrc/src/main/java/org/acme/build/SomeOtherBuildSrcClass.java') << """
        package org.acme.build;
        
        public class SomeOtherBuildSrcClass {
            public static void foo(String foo){
                System.out.println(foo);
            }
        }
        """
        settingsFile << """
            buildscript {
                repositories {
                    mavenCentral()
                }
                
                dependencies {  
                    classpath 'org.apache.commons:commons-lang3:3.9'                
                }
            }

            org.apache.commons.lang3.StringUtils.capitalize("bar")
        """

        buildFile << """
            org.acme.build.SomeOtherBuildSrcClass.foo("from build.gradle")
        """
        then:
        succeeds("tasks")
    }


    def "Using buildSrc resources in settings is deprecated"() {
        file('buildSrc/src/main/resources/org/acme/build/SomeResource.txt') << """
        // some resource content
        """
        settingsFile << """
            println 'settings resource loading'
            getClass().getClassLoader().getResource("org/acme/build/SomeResource.txt");
        """

        when:
        executer.expectDeprecationWarnings(1)
        succeeds("tasks")
        then:
        outputContains("Access to the buildSrc project and its dependencies in settings scripts has been deprecated.")
    }

    def "Using buildSrc dependencies in settings is deprecated"() {
        file('buildSrc/build.gradle') << """
            apply plugin: 'groovy'
            
            repositories {
                mavenCentral()
            }

            dependencies {  
                implementation 'org.apache.commons:commons-math3:3.6.1'                
            }
        """

        settingsFile << """
            org.apache.commons.math3.util.FastMath.nextUp(0.2)
        """

        when:
        executer.expectDeprecationWarnings(1)
        succeeds("tasks")
        then:
        outputContains("Access to the buildSrc project and its dependencies in settings scripts has been deprecated.")
    }

}
