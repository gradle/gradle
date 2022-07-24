/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.integtests.fixtures.executer;

import org.gradle.exemplar.model.Command;
import org.gradle.exemplar.model.Sample;
import org.gradle.exemplar.test.runner.SampleModifier;
import org.gradle.util.internal.GUtil;

import java.io.File;
import java.util.Properties;

/**
 * Gives the sample tests more metaspace, as we currently never collect old buildSrc/buildscript classloaders.
 */
public class MoreMemorySampleModifier implements SampleModifier {

    private static final String ORG_GRADLE_JVMARGS = "org.gradle.jvmargs";

    @Override
    public Sample modify(Sample sample) {
        File projectDir = sample.getProjectDir();
        giveMoreMemoryTo(projectDir);
        for (Command command : sample.getCommands()) {
            if (command.getExecutionSubdirectory() != null) {
                giveMoreMemoryTo(new File(projectDir, command.getExecutionSubdirectory()));
            }
        }
        return sample;
    }

    private void giveMoreMemoryTo(File projectDir) {
        File propertiesFile = new File(projectDir, "gradle.properties");
        Properties properties = propertiesFile.exists() ? GUtil.loadProperties(propertiesFile) : new Properties();
        String existingArgs = properties.getProperty(ORG_GRADLE_JVMARGS, "");
        properties.setProperty(ORG_GRADLE_JVMARGS, "-Xmx512m -XX:MaxMetaspaceSize=512m -XX:+HeapDumpOnOutOfMemoryError " + existingArgs);
        GUtil.saveProperties(properties, propertiesFile);
    }
}
