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

package gradlebuild.docs;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.UncheckedException;
import org.gradle.work.DisableCachingByDefault;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Writes the Exemplar test that is added to every snippet without its own sanity check: {@code gradle tasks -q}.
 */
@DisableCachingByDefault(because = "Writes a constant file, not worth caching")
public abstract class GenerateSnippetSanityCheck extends DefaultTask {
    private static final String SANITY_CHECK = "commands: [{\n"
        + "    executable: gradle\n"
        + "    args: tasks -q\n"
        + "}]\n";

    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @TaskAction
    public void generate() {
        try {
            Files.write(getOutputFile().get().getAsFile().toPath(), SANITY_CHECK.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }
}
