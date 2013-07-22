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
package org.gradle.openapi.wrappers.ui;

import org.gradle.gradleplugin.foundation.CommandLineArgumentAlteringListener;
import org.gradle.openapi.external.ui.CommandLineArgumentAlteringListenerVersion1;

/**
 * Wrapper to shield version changes in GradleTab from an external user of the gradle open API.
 */
public class CommandLineArgumentAlteringListenerWrapper implements CommandLineArgumentAlteringListener {
    private CommandLineArgumentAlteringListenerVersion1 listenerVersion1;

    public CommandLineArgumentAlteringListenerWrapper(CommandLineArgumentAlteringListenerVersion1 listenerVersion1) {
        this.listenerVersion1 = listenerVersion1;
    }

    /**
     * This is called when you can add additional command line arguments. Return any additional arguments to add. This doesn't modify the existing commands.
     *
     * @param commandLineArguments the command line to execute.
     * @return any command lines to add or null to leave it alone
     */
    public String getAdditionalCommandLineArguments(String commandLineArguments) {
        return listenerVersion1.getAdditionalCommandLineArguments(commandLineArguments);
    }
}
