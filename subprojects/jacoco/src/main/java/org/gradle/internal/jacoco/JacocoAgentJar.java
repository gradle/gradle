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
package org.gradle.internal.jacoco;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileOperations;
import org.gradle.api.specs.Spec;
import org.gradle.util.VersionNumber;

import java.io.File;
import javax.inject.Inject;

/**
 * Helper to resolve the {@code jacocoagent.jar} from inside of the {@code org.jacoco.agent.jar}.
 */
public class JacocoAgentJar {

    private static final VersionNumber V_0_6_2_0 = VersionNumber.parse("0.6.2.0");
    private static final VersionNumber V_0_7_6_0 = VersionNumber.parse("0.7.6.0");

    private final FileOperations fileOperations;
    private FileCollection agentConf;
    private File agentJar;

    /**
     * Constructs a new agent JAR wrapper.
     */
    @Inject
    public JacocoAgentJar(FileOperations fileOperations) {
        this.fileOperations = fileOperations;
    }

    /**
     * @return the configuration that the agent JAR is located in
     */
    public FileCollection getAgentConf() {
        return agentConf;
    }

    public void setAgentConf(FileCollection agentConf) {
        this.agentConf = agentConf;
    }

    /**
     * Unzips the resolved {@code org.jacoco.agent.jar} to retrieve the {@code jacocoagent.jar}.
     *
     * @return a file pointing to the {@code jacocoagent.jar}
     */
    public File getJar() {
        if (agentJar == null) {
            agentJar = fileOperations.zipTree(getAgentConf().getSingleFile()).filter(new Spec<File>() {
                @Override
                public boolean isSatisfiedBy(File file) {
                    return file.getName().equals("jacocoagent.jar");
                }
            }).getSingleFile();
        }
        return agentJar;
    }

    public boolean supportsJmx() {
        boolean pre062 = Iterables.any(getAgentConf(), new Predicate<File>() {
            @Override
            public boolean apply(File file) {
                return V_0_6_2_0.compareTo(extractVersion(file.getName())) > 0;
            }
        });
        return !pre062;
    }

    public boolean supportsInclNoLocationClasses() {
        boolean pre076 = Iterables.any(getAgentConf(), new Predicate<File>() {
            @Override
            public boolean apply(File file) {
                return V_0_7_6_0.compareTo(extractVersion(file.getName())) > 0;
            }
        });
        return !pre076;
    }

    public static VersionNumber extractVersion(String jarName) {
        // jarName format: org.jacoco.agent-<version>.jar
        int versionStart = "org.jacoco.agent-".length();
        int versionEnd = jarName.length() - ".jar".length();
        return VersionNumber.parse(jarName.substring(versionStart, versionEnd));
    }
}
