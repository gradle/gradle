/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.integtests

import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.executer.GradleDistributionExecuter
import org.junit.Rule
import org.junit.Test
import org.gradle.util.TestFile

class IncrementalJavaProjectBuildIntegrationTest {
    @Rule public final GradleDistribution distribution = new GradleDistribution()
    @Rule public final GradleDistributionExecuter executer = new GradleDistributionExecuter()

    @Test
    public void removesStateResources() {
        distribution.testFile('build.gradle') << 'apply plugin: \'java\''
        distribution.testFile('src/main/resources/org/gradle/resource.txt').createFile()

        executer.withTasks('classes').run()
        distribution.testFile('build/resources/main').assertHasDescendants('org/gradle/resource.txt')

        distribution.testFile('src/main/resources/org/gradle/resource.txt').assertIsFile().delete()
        distribution.testFile('src/main/resources/org/gradle/resource2.txt').createFile()

        executer.withTasks('classes').run()
        distribution.testFile('build/resources/main').assertHasDescendants('org/gradle/resource2.txt')
    }

    @Test
    public void doesNotRebuildJarIfSourceHasNotChanged() {
        // Use own home dir so we don't blast the shared one when we run with -C rebuild
        distribution.requireOwnUserHomeDir()

        distribution.testFile("src/main/java/BuildClass.java") << 'public class BuildClass { }'
        distribution.testFile("build.gradle") << "apply plugin: 'java'"
        distribution.testFile("settings.gradle") << "rootProject.name = 'project'"

        executer.withTasks("jar").run();

        TestFile jar = distribution.testFile("build/libs/project.jar");
        jar.assertIsFile();
        TestFile.Snapshot snapshot = jar.snapshot();

        executer.withTasks("jar").run();

        jar.assertHasNotChangedSince(snapshot);

        sleep 1000 // Some filesystems (ext3) have one-second granularity for lastModified, so bump the time to ensure we can detect a regenerated file
        executer.withArguments("--rerun-tasks").withTasks("jar").run();

        jar.assertHasChangedSince(snapshot);
        snapshot = jar.snapshot();

        executer.withTasks("jar").run();
        jar.assertHasNotChangedSince(snapshot);
    }
}
