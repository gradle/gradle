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

package org.gradle.gradlebuild.docs;

import org.apache.tools.ant.filters.ReplaceTokens;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Takes a rendered release notes HTML file and decorates it with extra elements/content/links.
 */
@CacheableTask
public abstract class DecorateReleaseNotes extends DefaultTask {
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getHtmlFile();

    @OutputFile
    public abstract RegularFileProperty getDestinationFile();

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract ConfigurableFileCollection getJquery();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getReleaseNotesJavascriptFile();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getReleaseNotesCssFile();

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getBaseCssFile();

    @Input
    public abstract MapProperty<String, String> getReplacementTokens();

    @TaskAction
    public void transform() {
        File destinationFile = getDestinationFile().get().getAsFile();

        getProject().copy(copySpec -> {
            copySpec.from(getHtmlFile());
            copySpec.into(destinationFile.getParentFile());
            copySpec.rename(s -> destinationFile.getName());

            // The order here is important! tokens are inserted by the transformer
            Map<String, Object> parameters = new HashMap<>();
            // NOTE: These parameter names _must_ match the setter names in ReleaseNotesTransformer
            parameters.put("baseCss", getBaseCssFile().get().getAsFile());
            parameters.put("releaseNotesCss", getReleaseNotesCssFile().get().getAsFile());
            parameters.put("releaseNotesJavascript", getReleaseNotesJavascriptFile().get().getAsFile());
            parameters.put("jquery", getJquery().getSingleFile());

            copySpec.filter(parameters, ReleaseNotesTransformer.class);
            copySpec.filter(Collections.singletonMap("tokens", getReplacementTokens().get()), ReplaceTokens.class);
        });
    }
}
