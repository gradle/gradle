/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.nativeplatform.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.nativeplatform.toolchain.internal.PrefixHeaderFileGeneratorUtil;

import java.io.File;
import java.util.Set;

/**
 * Generates a prefix header file from a list of headers to be precompiled.
 */
public class PrefixHeaderFileGenerateTask extends DefaultTask {
    @Input
    Set<String> headers;

    @OutputFile
    File prefixHeaderFile;

    @TaskAction
    void generatePrefixHeaderFile() {
        PrefixHeaderFileGeneratorUtil.generatePCHFile(headers, prefixHeaderFile);
    }

    public Set<String> getHeaders() {
        return headers;
    }

    public void setHeaders(Set<String> headers) {
        this.headers = headers;
    }

    public File getPrefixHeaderFile() {
        return prefixHeaderFile;
    }

    public void setPrefixHeaderFile(File prefixHeaderFile) {
        this.prefixHeaderFile = prefixHeaderFile;
    }
}
