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

import org.gradle.integtests.fixtures.AbstractIntegrationTest
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.test.fixtures.file.TestFile
import org.junit.Test

class IncrementalJavaProjectBuildIntegrationTest extends AbstractIntegrationTest {

    @Test
    public void removesStaleResources() {
        file('build.gradle') << 'apply plugin: \'java\''
        file('src/main/resources/org/gradle/resource.txt').createFile()

        executer.withTasks('classes').run()
        file('build/resources/main').assertHasDescendants('org/gradle/resource.txt')

        file('src/main/resources/org/gradle/resource.txt').assertIsFile().delete()
        file('src/main/resources/org/gradle/resource2.txt').createFile()

        executer.withTasks('classes').run()
        file('build/resources/main').assertHasDescendants('org/gradle/resource2.txt')
    }

    @Test
    @ToBeFixedForInstantExecution
    public void doesNotRebuildJarIfSourceHasNotChanged() {
        // Use own home dir so we don't blast the shared one when we run with -C rebuild
        executer.requireOwnGradleUserHomeDir()

        file("src/main/java/BuildClass.java") << 'public class BuildClass { }'
        file("build.gradle") << "apply plugin: 'java'"
        file("settings.gradle") << "rootProject.name = 'project'"

        executer.withTasks("jar").run();

        TestFile jar = file("build/libs/project.jar");
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
