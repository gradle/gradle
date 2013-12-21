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
package org.gradle.foundation;

import junit.framework.TestCase;
import org.gradle.logging.internal.LoggingCommandLineConverter;

import static org.gradle.foundation.CommandLineAssistant.breakUpCommandLine;

/**
 * This tests aspects of command line parsing that the UI does.
 */
public class CommandLineParsingTest extends TestCase {
    /**
     * This verifies that the user can override the current log level on a command line. The issue here is that the UI
     * defines a default log level that will always be used for all commands, but there are time when you want to
     * override it. This is useful if someone enters something into the command line tab or just wants to make a
     * favorite that always uses a specific log level. All they have to do is specify a log level on their command line
     * and the won't append one. This verifies that the function that does the check if its already defined is working.
     */
    public void testOverridingLogLevel() {
        //first try it with the log level at the end
        String commandLine = ":build:something -d";

        CommandLineAssistant commandLineAssistant = new CommandLineAssistant();
        String[] arguments = breakUpCommandLine(commandLine);
        assertTrue(commandLineAssistant.hasLogLevelDefined(arguments));

        //now try it with the log level in the middle
        commandLine = ":build:something -d :clean";
        arguments = breakUpCommandLine(commandLine);
        assertTrue(commandLineAssistant.hasLogLevelDefined(arguments));

        //now try it with the log level at the beginning
        commandLine = "-d :clean";
        arguments = breakUpCommandLine(commandLine);
        assertTrue(commandLineAssistant.hasLogLevelDefined(arguments));

        //now try it with 'info' instead of debug
        commandLine = "-i :clean";
        arguments = breakUpCommandLine(commandLine);
        assertTrue(commandLineAssistant.hasLogLevelDefined(arguments));

        //lastly verify it doesn't inadvertantly detect a log level
        commandLine = ":clean";
        arguments = breakUpCommandLine(commandLine);
        assertFalse(commandLineAssistant.hasLogLevelDefined(arguments));
    }

    /**
     * This verifies that the user can override the current stack trace level on a command line. The issue here is that
     * the UI defines a default stack trace level that will always be used for all commands, but there are time when you
     * want to override it. This is useful if someone enters something into the command line tab or just wants to make a
     * favorite that always uses a specific stack trace level. All they have to do is specify a stack trace level on
     * their command line and the won't append one. This verifies that the function that does the check if its already
     * defined is working.
     */
    public void testOverridingStackTraceLevel() {
        //first try it with the stack trace level at the end
        String commandLine = ":build:something -" + LoggingCommandLineConverter.FULL_STACKTRACE;

        CommandLineAssistant commandLineAssistant = new CommandLineAssistant();
        String[] arguments = breakUpCommandLine(commandLine);
        assertTrue(commandLineAssistant.hasShowStacktraceDefined(arguments));

        //now try it with the stack trace level in the middle
        commandLine = ":build:something -" + LoggingCommandLineConverter.FULL_STACKTRACE + " :clean";
        arguments = breakUpCommandLine(commandLine);
        assertTrue(commandLineAssistant.hasShowStacktraceDefined(arguments));

        //now try it with the stack trace level at the beginning
        commandLine = "-" + LoggingCommandLineConverter.FULL_STACKTRACE + " :clean";
        arguments = breakUpCommandLine(commandLine);
        assertTrue(commandLineAssistant.hasShowStacktraceDefined(arguments));

        //now try it with a different stack trace level
        commandLine = "-" + LoggingCommandLineConverter.STACKTRACE + " :clean";
        arguments = breakUpCommandLine(commandLine);
        assertTrue(commandLineAssistant.hasShowStacktraceDefined(arguments));

        //lastly verify it doesn't inadvertantly detect a stack trace
        commandLine = ":clean";
        arguments = breakUpCommandLine(commandLine);
        assertFalse(commandLineAssistant.hasShowStacktraceDefined(arguments));
    }
}
