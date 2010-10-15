/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.launcher;

import org.gradle.BuildExceptionReporter;
import org.gradle.StartParameter;
import org.gradle.logging.internal.StreamingStyledTextOutputFactory;

import java.util.Arrays;

/**
 * The main command-line entry-point for Gradle.
 *
 * @author Hans Dockter
 */
public class Main {
    private final String[] args;

    public Main(String[] args) {
        this.args = args;
    }

    public static void main(String[] args) {
        try {
            new Main(args).execute();
            System.exit(0);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            System.exit(1);
        }
    }

    public void execute() {
        BuildCompleter buildCompleter = createBuildCompleter();
        try {
            CommandLineActionFactory actionFactory = createActionFactory(buildCompleter);
            Runnable action = actionFactory.convert(Arrays.asList(args));
            action.run();
            buildCompleter.exit(null);
        } catch (Throwable e) {
            new BuildExceptionReporter(new StreamingStyledTextOutputFactory(System.err), new StartParameter()).reportException(e);
            buildCompleter.exit(e);
        }
    }

    CommandLineActionFactory createActionFactory(BuildCompleter buildCompleter) {
        return new CommandLineActionFactory(buildCompleter);
    }

    BuildCompleter createBuildCompleter() {
        return new ProcessExitBuildCompleter();
    }

    private static class ProcessExitBuildCompleter implements BuildCompleter {
        public void exit(Throwable failure) {
            System.exit(failure == null ? 0 : 1);
        }
    }
}
