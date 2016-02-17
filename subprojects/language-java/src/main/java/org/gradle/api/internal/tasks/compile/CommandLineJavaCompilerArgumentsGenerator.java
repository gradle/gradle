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
import org.gradle.internal.process.ArgCollector;
import org.gradle.internal.process.ArgWriter;

import java.io.File;
import java.io.Serializable;
import java.util.List;

public class CommandLineJavaCompilerArgumentsGenerator implements CompileSpecToArguments<JavaCompileSpec>, Serializable {
    @Override
    public void collectArguments(JavaCompileSpec spec, ArgCollector collector) {
        for (String arg : generate(spec)) {
            collector.args(arg);
        }
    }

    public Iterable<String> generate(JavaCompileSpec spec) {
        List<String> launcherOptions = new JavaCompilerArgumentsBuilder(spec).includeLauncherOptions(true).includeMainOptions(false).includeClasspath(false).includeCustomizations(false).build();
        List<String> remainingArgs = new JavaCompilerArgumentsBuilder(spec).includeSourceFiles(true).build();
        Iterable<String> allArgs = Iterables.concat(launcherOptions, remainingArgs);
        if (exceedsWindowsCommandLineLengthLimit(allArgs)) {
            return Iterables.concat(launcherOptions, shortenArgs(spec.getTempDir(), remainingArgs));
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
            if (length > 1500) {
                return true;
            }
        }
        return false;
    }

    private Iterable<String> shortenArgs(File tempDir, List<String> args) {
        // for command file format, see http://docs.oracle.com/javase/6/docs/technotes/tools/windows/javac.html#commandlineargfile
        // use platform character and line encoding
        return ArgWriter.argsFileGenerator(new File(tempDir, "java-compiler-args.txt"), ArgWriter.unixStyleFactory()).transform(args);
    }
}
