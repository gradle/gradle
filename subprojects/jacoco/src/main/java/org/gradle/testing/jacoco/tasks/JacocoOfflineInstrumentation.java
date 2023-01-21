/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.testing.jacoco.tasks;

import org.gradle.api.Incubating;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.internal.project.IsolatedAntBuilder;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.IgnoreEmptyDirectories;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.jacoco.AntJacocoInstrument;

import javax.inject.Inject;
import java.io.File;

/**
 * Task for applying Jacoco offline instrumentation to a collection of classes.
 *
 * @since 8.1
 */
@Incubating
@CacheableTask
public abstract class JacocoOfflineInstrumentation extends JacocoBase {

    @IgnoreEmptyDirectories
    @PathSensitive(PathSensitivity.RELATIVE)
    @InputFiles
    public abstract ConfigurableFileCollection getInputClassDirs();

    public void sourceSets(final SourceSet... sourceSets) {
        for (final SourceSet sourceSet : sourceSets) {
            getInputClassDirs().from(sourceSet.getOutput().getClassesDirs());
        }
    }

    @OutputDirectory
    public abstract DirectoryProperty getOutputDir();

    @Inject
    protected abstract IsolatedAntBuilder getAntBuilder();

    @TaskAction
    public void generate() {
        Directory outputDir = getOutputDir().get();
        getProject().delete(outputDir);

        new AntJacocoInstrument(getAntBuilder()).execute(
            getJacocoClasspath(),
            getInputClassDirs().filter(File::exists),
            outputDir
        );
    }
}
