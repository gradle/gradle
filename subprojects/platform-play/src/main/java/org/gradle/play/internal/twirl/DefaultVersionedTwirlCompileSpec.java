/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.play.internal.twirl;

import org.gradle.api.tasks.compile.BaseForkOptions;

import java.io.File;

public abstract class DefaultVersionedTwirlCompileSpec extends DefaultTwirlCompileSpec {
    private final String additionalImports;
    private final String formatterType;

    public DefaultVersionedTwirlCompileSpec(File sourceDirectory, Iterable<File> sources, File destinationDir, BaseForkOptions forkOptions, boolean javaProject) {
        this(sourceDirectory, sources, destinationDir, null, null, forkOptions, javaProject);
    }

    public DefaultVersionedTwirlCompileSpec(File sourceDirectory, Iterable<File> sources, File destinationDir, String additionalImports, String formatterType, BaseForkOptions forkOptions, boolean javaProject) {
        super(sourceDirectory, sources, destinationDir, forkOptions, javaProject);
        if (additionalImports != null) {
            this.additionalImports = additionalImports;
        } else {
            String defaultFormat = "html";
            if (javaProject) {
                this.additionalImports = defaultJavaAdditionalImports(defaultFormat);
            } else {
                this.additionalImports = defaultScalaAdditionalImports(defaultFormat);
            }
        }
        if (formatterType != null) {
            this.formatterType = formatterType;
        } else {
            this.formatterType = defaultFormatterType();
        }
    }

    protected abstract String defaultFormatterType();

    protected abstract String defaultJavaAdditionalImports(String format);

    protected abstract String defaultScalaAdditionalImports(String format);

    public String getFormatterType() {
        return this.formatterType;
    }

    public String getAdditionalImports() {
        return this.additionalImports;
    }
}
