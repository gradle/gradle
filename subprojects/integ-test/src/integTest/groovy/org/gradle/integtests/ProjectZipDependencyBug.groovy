/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.integtests

import spock.lang.*
import org.gradle.integtests.fixtures.*
import org.gradle.integtests.fixtures.internal.*

/**
 * Bug with m5 that I encountered doing the Geb release.
 * 
 * Check the output for this test. It looks like that no ivy.xml is being created for the project dependency, maybe because it's a zip.
 */
@Ignore
class ProjectZipDependencyBug extends AbstractIntegrationSpec {

    def "something's weird about project zip dependencies"() {
        given:
        file("settings.gradle") << "include 'a'; include 'b'"
        file("a/some.txt") << "foo"
        file("a/build.gradle") << """
            group = "g"
            version = 1.0
            
            apply plugin: 'base'
            task zip(type: Zip) {
                from "some.txt"
            }

            artifacts {
                archives zip
            }
        """
        file("b/build.gradle") << """
            configurations { conf }
            dependencies {
                conf project(":a")
            }
            
            task copyZip(type: Copy) {
                from configurations.conf
                into "\$buildDir/copied"
            }
        """
        
        when:
        succeeds ":b:copyZip"
        
        then:
        // this will fail because the project dependency won't exist in the configuration,
        // so the copy spec has no inputs, so it is skipped.
        ":b:copyZip" in  nonSkippedTasks 
        
        and:
        file("b/build/copied/a-1.0.zip").exists()
    }

}