/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.util.VersionNumber

/**
 * Helper to resolve the {@code jacocoagent.jar} from inside
 * of the {@code org.jacoco.agent.jar}.
 */
class JacocoAgentJar {
    private final Project project
    private File agentJar
    FileCollection agentConf

    /**
     * Constructs a new agent JAR wrapper.
     * @param project a project that can be used to resolve files
     * @param agentConf the configuration that the agent JAR is located in
     */
    JacocoAgentJar(Project project) {
        this.project = project
    }

    /**
     * Unzips the resolved {@code org.jacoco.agent.jar} to retrieve
     * the {@code jacocoagent.jar}.
     * @return a file pointing to the {@code jacocoagent.jar}
     */
    File getJar() {
        if (!agentJar) {
            agentJar = project.zipTree(getAgentConf().singleFile).filter { it.name == 'jacocoagent.jar' }.singleFile
        }
        return agentJar
    }

    boolean supportsJmx() {
        def pre062 = getAgentConf().any {
            VersionNumber.parse("0.6.2.0") > extractVersion(it.name)
        }
        return !pre062
    }

    boolean supportsInclNoLocationClasses() {
        def pre076 = getAgentConf().any {
            VersionNumber.parse("0.7.6.0") > extractVersion(it.name)
        }
        return !pre076;
    }

    static VersionNumber extractVersion(String jarName) {
        // jarName format: org.jacoco.agent-<version>.jar
        int versionStart = "org.jacoco.agent-".length()
        int versionEnd = jarName.length() - ".jar".length()
        return VersionNumber.parse(jarName.substring(versionStart, versionEnd))
    }
}
