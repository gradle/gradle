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
package org.gradle.launcher.daemon

import org.gradle.configuration.GradleLauncherMetaData
import org.gradle.integtests.fixtures.GradleDistribution
import org.gradle.launcher.exec.DefaultBuildActionParameters
import org.gradle.tooling.internal.provider.ConfiguringBuildAction
import org.gradle.tooling.internal.provider.ExecuteBuildAction
import org.gradle.launcher.daemon.client.EmbeddedDaemonClientServices
import org.gradle.launcher.daemon.client.DaemonClient
import org.gradle.launcher.daemon.registry.DaemonRegistry

import org.junit.Rule
import spock.lang.Specification

/**
 * Exercises the basic mechanics using an embedded daemon.
 * 
 * @todo Test stdio (what should println do in the daemon threads?)
 * @todo launching multiple embedded daemons with the same registry
 */
class EmbeddedDaemonSmokeTest extends Specification {

    @Rule public final GradleDistribution distribution = new GradleDistribution()

    def daemonClientServices = new EmbeddedDaemonClientServices()
    
    def "run build"() {
        given:
        def action = new ConfiguringBuildAction(distribution.gradleHomeDir, distribution.testDir, false, new ExecuteBuildAction(["echo"]))
        def parameters = new DefaultBuildActionParameters(new GradleLauncherMetaData(), new Date().time, System.properties, System.getenv(), new File("."))
        
        and:
        def outputFile = distribution.testDir.file("output.txt")
        
        expect:
        !outputFile.exists()
        
        and:
        distribution.testDir.file("build.gradle") << """
            task echo << {
                file("output.txt").write "Hello!"
            }
        """
        
        when:
        daemonClientServices.get(DaemonClient).execute(action, parameters)
        
        then:
        outputFile.exists() && outputFile.text == "Hello!"
    }
    
    def cleanup() {
        daemonClientServices.get(DaemonRegistry).stopDaemons()
    }

}