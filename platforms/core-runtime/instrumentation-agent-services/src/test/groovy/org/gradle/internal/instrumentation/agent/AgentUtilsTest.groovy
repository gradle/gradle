/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.instrumentation.agent

import spock.lang.Specification

class AgentUtilsTest extends Specification {

    def "third-party Java agents are detected"() {
        expect:
        AgentUtils.isThirdPartyAgentSwitch("-javaagent:/path/to/some-agent.jar")
        AgentUtils.isThirdPartyAgentSwitch("-javaagent:/path/to/some-agent.jar=options")
    }

    def "Gradle's own instrumentation agent is not a third-party agent"() {
        expect:
        !AgentUtils.isThirdPartyAgentSwitch("-javaagent:/path/to/${AgentUtils.AGENT_MODULE_NAME}-1.0.jar")
    }

    def "can detect gradle agent switch"() {
        expect:
        AgentUtils.isGradleInstrumentationAgentSwitch("-javaagent:${AgentUtils.AGENT_MODULE_NAME}.jar")
        AgentUtils.isGradleInstrumentationAgentSwitch("-javaagent:${AgentUtils.AGENT_MODULE_NAME}-1.0.jar")
        AgentUtils.isGradleInstrumentationAgentSwitch("-javaagent:/path/to/gradle/${AgentUtils.AGENT_MODULE_NAME}-1.0.jar")
        AgentUtils.isGradleInstrumentationAgentSwitch("-javaagent:/path/to/gradle/${AgentUtils.AGENT_MODULE_NAME}-1.0.jar=options")
        AgentUtils.isGradleInstrumentationAgentSwitch("-javaagent:C:\\path\\to\\gradle\\${AgentUtils.AGENT_MODULE_NAME}-1.0.jar")

        !AgentUtils.isGradleInstrumentationAgentSwitch("-javaagent:/path/to/another-agent.jar")
        !AgentUtils.isGradleInstrumentationAgentSwitch("-javaagent:/path/to/another-agent.jar=exclude=${AgentUtils.AGENT_MODULE_NAME}-1.0.jar")
    }

    def "generic native JVMTI agents are treated as third-party agents"() {
        expect:
        AgentUtils.isThirdPartyAgentSwitch("-agentlib:asan")
        AgentUtils.isThirdPartyAgentSwitch("-agentpath:/usr/lib/libasan.so=options")
        // A library whose name merely contains an exempt name but isn't the exempt agent library is not exempt.
        AgentUtils.isThirdPartyAgentSwitch("-agentpath:/usr/lib/libmyjdwp.so")
        AgentUtils.isThirdPartyAgentSwitch("-agentpath:/usr/lib/libasyncProfilerHelper.so")
    }

    def "the JDWP debug agent is exempt from third-party detection: #jvmArg"() {
        expect:
        !AgentUtils.isThirdPartyAgentSwitch(jvmArg)

        where:
        jvmArg << [
            "-agentlib:jdwp",
            "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005",
            "-agentpath:/usr/lib/jvm/temurin-17/lib/libjdwp.so=transport=dt_socket,server=y,suspend=n",
            "-agentpath:/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home/lib/libjdwp.dylib=transport=dt_socket",
            "-agentpath:C:\\Program Files\\Java\\jdk-17\\bin\\jdwp.dll=transport=dt_socket,server=y",
        ]
    }

    def "the async-profiler agent is exempt from third-party detection: #jvmArg"() {
        expect:
        !AgentUtils.isThirdPartyAgentSwitch(jvmArg)

        where:
        jvmArg << [
            "-agentlib:asyncProfiler",
            "-agentlib:asyncProfiler=start,event=cpu,file=profile.html",
            "-agentpath:/opt/async-profiler/lib/libasyncProfiler.so=start,event=cpu,file=profile.jfr",
            "-agentpath:/opt/async-profiler/lib/libasyncProfiler.dylib=start,event=alloc,file=profile.html",
        ]
    }

    def "non-agent JVM arguments are not third-party agents: #jvmArg"() {
        expect:
        !AgentUtils.isThirdPartyAgentSwitch(jvmArg)

        where:
        jvmArg << ["-Xmx2g", "-Dfoo=bar", "-ea", "--add-opens=java.base/java.lang=ALL-UNNAMED"]
    }
}
