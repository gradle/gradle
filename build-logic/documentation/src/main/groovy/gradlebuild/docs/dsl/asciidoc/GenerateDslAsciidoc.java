/*
 * Copyright 2021 the original author or authors.
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

package gradlebuild.docs.dsl.asciidoc;

import gradlebuild.docs.dsl.source.model.ClassMetaData;
import gradlebuild.docs.model.SimpleClassMetaDataRepository;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

@CacheableTask
abstract public class GenerateDslAsciidoc extends DefaultTask {
    @PathSensitive(PathSensitivity.NONE)
    @InputFile
    public abstract RegularFileProperty getClassMetaDataFile();

    @OutputDirectory
    public abstract DirectoryProperty getDestinationDirectory();

    @TaskAction
    public void generateAsciidocs() {
        SimpleClassMetaDataRepository<ClassMetaData> classRepository = new SimpleClassMetaDataRepository<>();
        classRepository.load(getClassMetaDataFile().get().getAsFile());
        Path destinationDirectory = getDestinationDirectory().get().getAsFile().toPath();

        DslDocRenderer generator = new DslDocRenderer();
        classRepository.each(classMetaData -> {
            try (Writer writer = Files.newBufferedWriter(destinationDirectory.resolve(classMetaData.getClassName() + ".adoc"))) {
                generator.mergeContent(classMetaData, writer);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}
