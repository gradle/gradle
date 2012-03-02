/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.tasks.compile;

import com.google.common.collect.Iterables;

import org.gradle.api.internal.file.TemporaryFileProvider;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;

public class CommandLineJavaCompilerArgumentsGenerator {
    private final TemporaryFileProvider tempFileProvider;

    public CommandLineJavaCompilerArgumentsGenerator(TemporaryFileProvider tempFileProvider) {
        this.tempFileProvider = tempFileProvider;
    }

    public Iterable<String> generate(JavaCompileSpec spec) {
        JavaCompilerArgumentsBuilder builder = new JavaCompilerArgumentsBuilder(spec);
        List<String> launcherOptions = builder.includeLauncherOptions(true).includeMainOptions(false).includeSourceFiles(false).build();
        List<String> remainingArgs = builder.includeLauncherOptions(false).includeMainOptions(true).includeSourceFiles(true).build();
        Iterable<String> allArgs = Iterables.concat(launcherOptions, remainingArgs);
        if (exceedsWindowsCommandLineLengthLimit(allArgs)) {
            return Iterables.concat(launcherOptions, shortenArgs(remainingArgs));
        }
        return allArgs;
    }

    private boolean exceedsWindowsCommandLineLengthLimit(Iterable<String> args) {
        int length = 0;
        for (String arg : args) {
            length += arg.length() + 1;
            // limit is 2047 on older Windows systems, and 8191 on newer ones
            // http://support.microsoft.com/kb/830473
            // let's play it safe, no need to optimize
            if (length > 1500) { return true; }
        }
        return false;
    }

    private Iterable<String> shortenArgs(List<String> args) {
        File file = tempFileProvider.createTemporaryFile("compile-args", null, "java-compiler");
        // use platform character and line encoding
        GFileUtils.writeLines(file, quoteArgs(args));
        return Collections.singleton("@" + file.getPath());
    }

    private List<String> quoteArgs(List<String> args) {
        ListIterator<String> iterator = args.listIterator();
        while (iterator.hasNext()) {
            String arg = iterator.next();
            // assumption: blindly quoting all args is fine
            // as for how to quote, see http://docs.oracle.com/javase/6/docs/technotes/tools/windows/javac.html#commandlineargfile
            iterator.set("\"" + arg.replace("\\", "\\\\") + "\"");
        }
        return args;
    }
}
