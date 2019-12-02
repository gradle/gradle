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
import org.gradle.api.tasks.TaskAction;

public abstract class JsoupPostProcess extends DefaultTask {
    public abstract RegularFileProperty getHtmlFile();

    public abstract RegularFileProperty getDestinationFile();

    public abstract ConfigurableFileCollection getTransforms();

    public abstract MapProperty<String, String> getReplacementTokens();

    @TaskAction
    public void transform() {
        getProject().copy(copySpec -> {
            // TODO:
            copySpec.from(getHtmlFile());
            copySpec.into(getDestinationFile().get().getAsFile().getParentFile());
            copySpec.filter(getReplacementTokens().get(), ReplaceTokens.class);
        });
    }
}
