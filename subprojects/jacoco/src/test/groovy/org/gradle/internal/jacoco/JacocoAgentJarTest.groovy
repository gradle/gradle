/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.jacoco

import org.gradle.api.internal.file.collections.SimpleFileCollection
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.Unroll

class JacocoAgentJarTest extends Specification {
    def project = ProjectBuilder.builder().build()
    def jacocoAgentJar = new JacocoAgentJar(project)

    @Unroll
    def "versions >= 0.6.2 support jmx #version -> #jmxSupport"() {
        given:
        def agentJarName = "org.jacoco.agent-${version}.jar"
        jacocoAgentJar.agentConf = new SimpleFileCollection(project.file(agentJarName))

        expect:
        jacocoAgentJar.supportsJmx() == jmxSupport

        where:
        version               | jmxSupport
        '0.5.10.201208310627' | false
        '0.6.0.201210061924'  | false
        '0.6.2.201302030002'  | true
        '0.7.1.201405082137'  | true
        '0.7.6.201602180812'  | true
        '0.7.8'               | true
    }

    @Unroll
    def "versions >= 0.7.6 support include no location classes #version -> #incNoLocationClassesSupport"() {
        given:
        def agentJarName = "org.jacoco.agent-${version}.jar"
        jacocoAgentJar.agentConf = new SimpleFileCollection(project.file(agentJarName))

        expect:
        jacocoAgentJar.supportsInclNoLocationClasses() == incNoLocationClassesSupport

        where:
        version               | incNoLocationClassesSupport
        '0.5.10.201208310627' | false
        '0.6.0.201210061924'  | false
        '0.6.2.201302030002'  | false
        '0.7.1.201405082137'  | false
        '0.7.6.201602180812'  | true
        '0.7.8'               | true
    }
}
