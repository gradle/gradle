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

package org.gradle.api.plugins.osgi

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

class OsgiPluginIntegrationSpec extends AbstractIntegrationSpec {

    @Issue("http://issues.gradle.org/browse/GRADLE-2237")
    def "can set modelled manifest properties with instruction"() {
        given:
        buildFile << """
            version = "1.0"
            group = "foo"
            apply plugin: "java"
            apply plugin: "osgi"
                            
            jar {
                manifest {
                    version = "3.0"
                    instructionReplace("Bundle-Version", "2.0")
                    instructionReplace("Bundle-SymbolicName", "bar")
                }
            }

            assert jar.manifest.symbolicName.startsWith("bar") // GRADLE-2446
        """
        
        and:
        file("src/main/java/Thing.java") << "public class Thing {}"
        
        when:
        run "jar"

        def manifestText = file("build/tmp/jar/MANIFEST.MF").text
        then:
        manifestText.contains("Bundle-Version: 2.0")
        manifestText.contains("Bundle-SymbolicName: bar")
    }

    @Issue("http://issues.gradle.org/browse/GRADLE-2237")
    def "jar task remains incremental"() {
        given:
        // Unsure why, but this problem doesn't show if we don't wait a little bit
        // before the next execution.
        //
        // The value that's used is comes from aQute.lib.osgi.Analyzer#calcManifest()
        // and is set to the current time. I don't have an explanation for why the sleep is needed.
        // It needs to be about 1000 on my machine.
        def sleepTime = 1000

        buildFile << """
            apply plugin: "java"
            apply plugin: "osgi"

            jar {
                manifest {
                    instruction "Bnd-LastModified", "123"
                }
            }
        """

        and:
        file("src/main/java/Thing.java") << "public class Thing {}"

        when:
        run "jar"
        
        then:
        ":jar" in nonSkippedTasks
        
        when:
        sleep sleepTime
        run "jar"

        then:
        ":jar" in skippedTasks

        when:
        sleep sleepTime
        run "clean", "jar"

        then:
        ":jar" in nonSkippedTasks
    }

    private waitForMinimumBndLastModifiedInterval() {

        sleep 1000
    }

}
