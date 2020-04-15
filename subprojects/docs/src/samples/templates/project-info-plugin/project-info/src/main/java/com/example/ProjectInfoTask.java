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

package com.example;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.api.tasks.options.OptionValues;
import java.util.EnumSet;
import java.util.Collection;

class ProjectInfoTask extends DefaultTask {

    enum Format {
        PLAIN, JSON
    }

    private Format format = Format.PLAIN;

    public ProjectInfoTask() {
    }

    @Option(option = "format", description = "Output format of the project information.")
    void setFormat(Format format) {
        this.format = format;
    }

    @OptionValues("format")
    Collection<Format> getSupportedFormats() {
        return EnumSet.allOf(Format.class);
    }

    @TaskAction
    void projectInfo() {
        switch (format) {
            case PLAIN:
                System.out.println(getProject().getName() + ":" + getProject().getVersion());
                break;
            case JSON:
                System.out.println("{\n" +
                    "    \"projectName\": \"" + getProject().getName() + "\"\n" +
                    "    \"version\": \"" + getProject().getVersion() + "\"\n}");
                break;
            default:
                throw new IllegalArgumentException("Unsupported format: " + format);
        }
    }

}
