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

package org.gradle.launcher.cli;

import org.gradle.cli.CommandLineParser;
import org.gradle.cli.ParsedCommandLine;
import org.gradle.gradleplugin.userinterface.swing.standalone.BlockingApplication;
import org.gradle.util.DeprecationLogger;

class GuiActionsFactory implements CommandLineAction {
    private static final String GUI = "gui";

    public void configureCommandLineParser(CommandLineParser parser) {
        parser.option(GUI).hasDescription("Launches the Gradle GUI.");
    }

    public Runnable createAction(CommandLineParser parser, ParsedCommandLine commandLine) {
        if (commandLine.hasOption(GUI)) {
            DeprecationLogger.nagUserOfToolReplacedWithExternalOne("Gradle GUI", "an IDE with support for Gradle e.g. Eclipse, IntelliJ or NetBeans");
            return new ShowGuiAction();
        }
        return null;
    }

    static class ShowGuiAction implements Runnable {
        public void run() {
            BlockingApplication.launchAndBlock();
        }
    }
}
