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

package gradlebuild.docs;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;

@CacheableTask
public abstract class GenerateDocInfo extends DefaultTask {
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getDocumentationFiles();

    @Internal
    public abstract DirectoryProperty getDocumentationRoot();

    @OutputDirectory
    public abstract DirectoryProperty getDestinationDirectory();

    @Inject
    protected abstract FileSystemOperations getFs();

    @TaskAction
    public void generate() {
        // TODO: This could probably use InputChanges API
        File destinationDirectory = getDestinationDirectory().get().getAsFile();
        getFs().delete(spec -> spec.delete(destinationDirectory));
        destinationDirectory.mkdirs();

        Path adocDir = getDocumentationRoot().get().getAsFile().toPath();
        getDocumentationFiles().getAsFileTree().matching(pattern -> pattern.include("**/*.adoc")).forEach(adocFile -> {
            String adocFileName = adocFile.getName();
            // getting_started.adoc -> getting_started-docinfo.html
            String docInfoName = adocFileName.substring(0, adocFileName.lastIndexOf('.')) + "-docinfo.html";
            String relativePath = adocDir.relativize(adocFile.toPath()).toString();
            File docInfo = new File(destinationDirectory, docInfoName);
            try {
                Files.write(docInfo.toPath(), Collections.singleton(String.format("<meta name=\"adoc-src-path\" content=\"%s\">", relativePath)), StandardOpenOption.CREATE);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }
}
