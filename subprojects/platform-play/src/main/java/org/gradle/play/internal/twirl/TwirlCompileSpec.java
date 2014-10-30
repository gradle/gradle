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

import org.gradle.play.internal.PlayCompileSpec;

import java.io.File;
import java.io.Serializable;

/**
 * TODO for now hard wired options need to be configurable from task
 * */
public class TwirlCompileSpec implements PlayCompileSpec, Serializable {
    private final File sourceDirectory;
    private final Iterable<File> sources;
    private File destinationDir;

    /**
     * @TODO move default into versioned invocation spec
     * */
    private String formatterType = "play.twirl.api.HtmlFormat";
    private String additionalImports = "import controllers._";
    private String codec = "UTF-8";

    private boolean inclusiveDots;
    private boolean useOldParser;
    private String compilerVersion;
    private Iterable<File> compileClasspath;

    public Iterable<File> getSources() {
        return sources;
    }

    public TwirlCompileSpec(File sourceDirectory, Iterable<File> sources,  Iterable<File> compileClasspath, File destinationDir, String compilerVersion) {
        this(sourceDirectory, sources, compileClasspath, destinationDir, compilerVersion, false, false);
    }

    public TwirlCompileSpec(File sourceDirectory, Iterable<File> sources, Iterable<File> compileClasspath, File destinationDir, String compilerVersion, boolean inclusiveDots, boolean useOldParser) {
        this.sources = sources;
        this.compileClasspath = compileClasspath;
        this.destinationDir = destinationDir;
        this.sourceDirectory = sourceDirectory;
        this.compilerVersion = compilerVersion;
        this.inclusiveDots = inclusiveDots;
        this.useOldParser = useOldParser;
    }

    public File getDestinationDir(){
        return destinationDir;
    }

    public boolean isUseOldParser() {
        return useOldParser;
    }

    public boolean isInclusiveDots() {
        return inclusiveDots;
    }

    public String getCodec() {
        return codec;
    }

    public String getAdditionalImports() {
        return additionalImports;
    }

    public String getFormatterType() {
        return formatterType;
    }

    public File getSourceDirectory() {
        return sourceDirectory;
    }

    public String getCompilerVersion() {
        return compilerVersion;
    }

    public Iterable<File> getCompileClasspath() {
        return compileClasspath;
    }
}
