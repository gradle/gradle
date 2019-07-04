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

package org.gradle.api.plugins

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class BuildSrcDeprecatedInSettingsIntegrationTest extends AbstractIntegrationSpec {

    def "buildSrc classes used in settings is deprecated"() {
        file('buildSrc/build.gradle') << """
            apply plugin: 'groovy'
            
            repositories {
                mavenCentral()
            }

            dependencies {  
                compile 'org.apache.commons:commons-math3:3.6.1'                
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
        executer.expectDeprecationWarnings(2)
        succeeds("tasks")
        then:
        outputContains("Using buildSrc classes in settings has been deprecated. This is scheduled to be removed in Gradle 6.0. Do not use 'org.acme.build.SomeBuildSrcClass' in settings")
        outputContains("Using buildSrc classes in settings has been deprecated. This is scheduled to be removed in Gradle 6.0. Do not use 'org.apache.commons.math3.util.FastMath' in settings")
    }

}
