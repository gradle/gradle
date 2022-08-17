/*
 * Copyright 2022 the original author or authors.
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

import com.google.common.collect.ImmutableMap;
import org.gradle.api.JavaVersion;
import org.gradle.exemplar.model.Sample;
import org.gradle.exemplar.test.runner.SampleModifier;
import org.gradle.integtests.fixtures.AvailableJavaHomes;
import org.gradle.internal.jvm.Jvm;
import org.gradle.util.internal.GUtil;

import java.io.File;
import java.util.Map;
import java.util.Properties;

/**
 * Modifies runtime Java for samples that require Java11 or later if possible
 */
public class UseJdk11OrLaterSampleModifier implements SampleModifier {

    private static final Map<String, String> JAVA11_SAMPLES = ImmutableMap.<String, String>builder()
        .put("structuring-software-projects_groovy_build-android-app.sample", "android-app")
        .put("structuring-software-projects_kotlin_build-android-app.sample", "android-app")
        .put("structuring-software-projects_groovy_umbrella-build.sample", "")
        .put("structuring-software-projects_kotlin_umbrella-build.sample", "")
        .build();

    private static final String ORG_GRADLE_JAVA_HOME = "org.gradle.java.home";

    @Override
    public Sample modify(Sample sample) {
        if (JAVA11_SAMPLES.containsKey(sample.getId()) && !Jvm.current().getJavaVersion().isCompatibleWith(JavaVersion.VERSION_11)) {
            File workingDir = new File(sample.getProjectDir(), JAVA11_SAMPLES.get(sample.getId()));
            maybeSetJava11(workingDir);
        }
        return sample;
    }

    private void maybeSetJava11(File workingDir) {
        Jvm jdk11 = AvailableJavaHomes.getJdk11();
        if (jdk11 != null) {
            File propertiesFile = new File(workingDir, "gradle.properties");
            Properties properties = propertiesFile.exists() ? GUtil.loadProperties(propertiesFile) : new Properties();
            properties.setProperty(ORG_GRADLE_JAVA_HOME, jdk11.getJavaHome().getAbsolutePath());
            GUtil.saveProperties(properties, propertiesFile);
        }
    }
}
