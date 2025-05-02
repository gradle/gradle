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

package org.gradle.api.plugins.antlr.internal;

import com.google.common.collect.Lists;
import org.gradle.api.file.FileCollection;
import org.gradle.api.plugins.antlr.AntlrTask;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class AntlrSpecFactory {

    public AntlrSpec create(AntlrTask antlrTask, Set<File> grammarFiles, FileCollection sourceSetDirectories) {
        List<String> arguments = Lists.newLinkedList(antlrTask.getArguments());

        if (antlrTask.isTrace() && !arguments.contains("-trace")) {
            arguments.add("-trace");
        }
        if (antlrTask.isTraceLexer() && !arguments.contains("-traceLexer")) {
            arguments.add("-traceLexer");
        }
        if (antlrTask.isTraceParser() && !arguments.contains("-traceParser")) {
            arguments.add("-traceParser");
        }
        if (antlrTask.isTraceTreeWalker() && !arguments.contains("-traceTreeWalker")) {
            arguments.add("-traceTreeWalker");
        }
        Set<File> sourceSetDirectoriesFiles;
        if (sourceSetDirectories == null) {
            sourceSetDirectoriesFiles = Collections.emptySet();
        } else {
            sourceSetDirectoriesFiles = sourceSetDirectories.getFiles();
        }

        return new AntlrSpec(arguments, grammarFiles, sourceSetDirectoriesFiles, antlrTask.getOutputDirectory(), antlrTask.getMaxHeapSize());
    }
}
