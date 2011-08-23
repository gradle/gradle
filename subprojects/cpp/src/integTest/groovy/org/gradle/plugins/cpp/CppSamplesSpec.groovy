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
package org.gradle.plugins.cpp

import org.gradle.integtests.fixtures.*
import org.gradle.integtests.fixtures.internal.*
import org.junit.*

class CppSamplesSpec extends AbstractIntegrationSpec {

    @Rule public final Sample exewithlib = new Sample('cpp/exewithlib')
    @Rule public final Sample dependencies = new Sample('cpp/dependencies')
    @Rule public final Sample exe = new Sample('cpp/exe')

    def "exe with lib"() {
        given:
        sample exewithlib

        when:
        run "build"

        then:
        ":exe:compileMain" in executedTasks

        and:
        file("cpp", "exewithlib", "exe", "build", "binaries", "main").exec().out == "Hello, World!\n"
    }
    
    def "dependencies"() {
        given:
        sample dependencies
        
        when:
        run ":lib:uploadArchives", ":exe:compileMain", ":exe:uploadArchives"
        
        then:
        ":exe:mainExtractHeaders" in nonSkippedTasks
        ":exe:compileMain" in nonSkippedTasks
        
        and:
        file("cpp", "dependencies", "exe", "build", "binaries", "main").exec().out == "Hello, World!\n"
        file("cpp", "dependencies", "exe", "build", "repo", "dependencies", "exe", "1.0", "exe-1.0.exe").exists()
    }
    
    def "exe"() {
        given:
        sample exe
        
        when:
        run "compileMain"
        
        then:
        ":compileMain" in nonSkippedTasks
        
        and:
        file("cpp", "exe", "build", "binaries", "main").exec().out == "Hello, World!\n"
    }
    
}