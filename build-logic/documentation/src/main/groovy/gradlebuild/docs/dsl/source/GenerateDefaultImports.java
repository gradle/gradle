/*
 * Copyright 2020 the original author or authors.
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

package gradlebuild.docs.dsl.source;

import org.gradle.api.DefaultTask;
import org.gradle.api.NonNullApi;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashSet;
import java.util.Set;

@NonNullApi
@CacheableTask
public abstract class GenerateDefaultImports extends DefaultTask {
    @PathSensitive(PathSensitivity.NONE)
    @InputFile
    public abstract RegularFileProperty getMetaDataFile();

    @OutputFile
    public abstract RegularFileProperty getImportsDestFile();

    /**
     * Package name can end with '.**' to exclude subpackages as well.
     */
    @Input
    public abstract SetProperty<String> getExcludedPackages();

    @TaskAction
    public void generate() throws IOException {
        final Set<String> packages = new LinkedHashSet<>();
        ClassMetaDataUtil.extractFromMetadata(getMetaDataFile().getAsFile().get(), getExcludedPackages().get(), classMetaData -> packages.add(classMetaData.getPackageName()));

        try (PrintWriter writer = new PrintWriter(new FileWriter(getImportsDestFile().getAsFile().get()))) {
            for (String packageName : packages) {
                writer.print("import ");
                writer.print(packageName);
                writer.println(".*");
            }
        }
    }
}
