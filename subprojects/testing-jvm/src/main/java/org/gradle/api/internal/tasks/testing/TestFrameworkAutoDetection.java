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

import org.gradle.api.file.EmptyFileVisitor;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.internal.tasks.testing.junit.JUnitTestFramework;
import org.gradle.api.internal.tasks.testing.junitplatform.JUnitPlatformTestFramework;
import org.gradle.api.internal.tasks.testing.testng.TestNGTestFramework;
import org.gradle.api.tasks.testing.Test;
import org.gradle.internal.MutableReference;

import javax.annotation.Nonnull;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestFrameworkAutoDetection {

    private static final Pattern VERSIONED_JAR_PATTERN = Pattern.compile("([a-z-]+)-\\d.*\\.jar");

    public static void configure(Test task) {
        Class<? extends TestFramework> testFrameworkClass = detectTestFrameworkFromClasspath(task);
        if (JUnitTestFramework.class.equals(testFrameworkClass)) {
            task.useJUnit();
        } else if (TestNGTestFramework.class.equals(testFrameworkClass)) {
            task.useTestNG();
        } else if (JUnitPlatformTestFramework.class.equals(testFrameworkClass)) {
            task.useJUnitPlatform();
        }
    }

    private static Class<? extends TestFramework> detectTestFrameworkFromClasspath(Test task) {
        final MutableReference<Class<? extends TestFramework>> testFramework = MutableReference.empty();
        testFramework.set(JUnitTestFramework.class);
        task.getClasspath().getAsFileTree().visit(new EmptyFileVisitor() {
            @Override
            public void visitFile(@Nonnull FileVisitDetails fileDetails) {
                Matcher matcher = VERSIONED_JAR_PATTERN.matcher(fileDetails.getName());
                if (matcher.matches()) {
                    String artifactName = matcher.group(1);
                    if ("junit-platform-engine".equals(artifactName)) {
                        testFramework.set(JUnitPlatformTestFramework.class);
                        fileDetails.stopVisiting();
                    } else if ("testng".equals(artifactName)) {
                        testFramework.set(TestNGTestFramework.class);
                    }
                }
            }
        });
        return testFramework.get();
    }

}
