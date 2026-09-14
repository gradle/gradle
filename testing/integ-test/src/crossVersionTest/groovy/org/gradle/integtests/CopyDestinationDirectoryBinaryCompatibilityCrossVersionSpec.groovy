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
package org.gradle.integtests

import org.gradle.integtests.fixtures.CrossVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetVersions

@TargetVersions("9.6.1")
class CopyDestinationDirectoryBinaryCompatibilityCrossVersionSpec extends CrossVersionIntegrationSpec {

    def "Copy and Sync subclasses compiled with an earlier Gradle can override destinationDir"() {
        given:
        file("producer/settings.gradle") << "rootProject.name = 'producer'"
        file("producer/build.gradle") << """
            plugins {
                id 'java-gradle-plugin'
            }
        """
        file("producer/src/main/java/SomePlugin.java") << """
            import org.gradle.api.Plugin;
            import org.gradle.api.Project;
            import org.gradle.api.file.DirectoryProperty;
            import org.gradle.api.tasks.Copy;
            import org.gradle.api.tasks.Internal;
            import org.gradle.api.tasks.Sync;

            import java.io.File;

            public class SomePlugin implements Plugin<Project> {
                public abstract static class CustomCopy extends Copy {
                    @Internal
                    public abstract DirectoryProperty getDefaultDestinationDirectory();

                    @Override
                    public File getDestinationDir() {
                        return getDefaultDestinationDirectory().get().getAsFile();
                    }
                }

                public abstract static class CustomSync extends Sync {
                    @Internal
                    public abstract DirectoryProperty getDefaultDestinationDirectory();

                    @Override
                    public File getDestinationDir() {
                        return getDefaultDestinationDirectory().get().getAsFile();
                    }
                }

                @Override
                public void apply(Project project) {
                    project.getTasks().register("customCopy", CustomCopy.class, task -> {
                        task.from(project.file("src"));
                        task.getDefaultDestinationDirectory().set(project.getLayout().getBuildDirectory().dir("copy"));
                    });
                    project.getTasks().register("customSync", CustomSync.class, task -> {
                        task.from(project.file("src"));
                        task.getDefaultDestinationDirectory().set(project.getLayout().getBuildDirectory().dir("sync"));
                    });
                }
            }
        """
        buildFile << """
            buildscript {
                dependencies { classpath files('producer/build/libs/producer.jar') }
            }

            apply plugin: SomePlugin
        """
        file("src/a.txt") << "a"

        when:
        version(previous).withTasks('assemble').inDirectory(file("producer")).run()
        version(current).withTasks('customCopy', 'customSync').run()

        then:
        file("build/copy/a.txt").text == "a"
        file("build/sync/a.txt").text == "a"

        when:
        def secondRun = version(current).withTasks('customCopy', 'customSync').run()

        then:
        secondRun.assertTasksSkipped(':customCopy', ':customSync')
    }
}
