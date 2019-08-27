/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.tasks.testing;

import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.testing.Test;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestFrameworkAutoDetection {

    private static final Pattern VERSIONED_JAR_PATTERN = Pattern.compile("([a-z-]+)-\\d.*\\.jar");

    public static void configure(Test task) {
        Set<String> artifacts = getArtifactNames(task.getClasspath());
        if (artifacts.contains("junit-platform-engine")
                && (!artifacts.contains("junit") || artifacts.contains("junit-vintage-engine"))) {
            task.useJUnitPlatform();
        } else if (artifacts.contains("testng") && !artifacts.contains("junit")) {
            task.useTestNG();
        } else {
            task.useJUnit();
        }
    }

    private static Set<String> getArtifactNames(FileCollection classpath) {
        Set<String> result = new LinkedHashSet<String>();
        for (File file : classpath.getFiles()) {
            if (file.isFile()) {
                Matcher matcher = VERSIONED_JAR_PATTERN.matcher(file.getName());
                if (matcher.matches()) {
                    String artifactName = matcher.group(1);
                    result.add(artifactName);
                }
            }
        }
        return result;
    }

}
