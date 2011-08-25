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
package org.gradle.launcher

import spock.lang.*

import org.gradle.integtests.fixtures.*
import org.gradle.integtests.fixtures.internal.*

/**
 * Verifies that Gradle doesn't pollute the system class loader.
 * 
 * This is important for plugins that need to use isolated class loaders to avoid conflicts.
 * 
 * When running without the daemon, success is dependant on the start scripts doing the right thing.
 * When running with the daemon, success is dependent on DaemonConnector forking the daemon process with the right classpath.
 * 
 * This test is not meaningfull when running the embedded integration test mode, so we short circuit in that case.
 */
class SystemClassLoaderTest extends AbstractIntegrationSpec {

    static heading = "systemClassLoader info"
    
    def "daemon bootstrap classpath is bare bones"() {
        given:
        buildFile << """
            task echo << { 
                def systemLoaderUrls = ClassLoader.systemClassLoader.URLs
                println "$heading"
                println systemLoaderUrls.size()
                println systemLoaderUrls[0]
            }
        """
        
        when:
        run "echo"
        
        then:
        def lines = output.readLines()
        lines.find { it == heading } // here for nicer output if the output isn't what we expect
        def headingIndex = lines.indexOf(heading)
        !forkingExecuter || lines[headingIndex + 1] == "1"
        !forkingExecuter || lines[headingIndex + 2].contains("gradle-launcher") 
    }

    boolean isForkingExecuter() {
        executer.type.forks
    }
}