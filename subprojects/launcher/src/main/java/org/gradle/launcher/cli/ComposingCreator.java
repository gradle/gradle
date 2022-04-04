/*
 * Copyright 2022 the original author or authors.
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

import org.gradle.api.Action;
import org.gradle.cli.CommandLineParser;
import org.gradle.cli.ParsedCommandLine;
import org.gradle.internal.Actions;
import org.gradle.launcher.bootstrap.ExecutionListener;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class ComposingCreator implements CommandLineActionCreator {
    private final List<? extends CommandLineActionCreator> delegates;

    public ComposingCreator(List<? extends CommandLineActionCreator> delegates) {
        this.delegates = new ArrayList<>(delegates);
    }

    @Override
    public void configureCommandLineParser(CommandLineParser parser) {
        // No-op
    }

    @Nullable
    @Override
    public Action<? super ExecutionListener> createAction(CommandLineParser parser, ParsedCommandLine commandLine) {
        if (commandLine.hasOption(DefaultCommandLineActionFactory.VERSION_CONTINUE)) {
            Action<? super ExecutionListener> action1 = Actions.toAction(new DefaultCommandLineActionFactory.ShowVersionAction());
            Action<? super ExecutionListener> action2 = createSecondAction(parser, commandLine);
            if (action1 != null && action2 != null) {
                return Actions.composite(action1, action2);
            }
        }
        return null;
    }

    private Action<? super ExecutionListener> createSecondAction(CommandLineParser parser, ParsedCommandLine commandLine) {
        for (CommandLineActionCreator actionCreator : delegates) {
            Action<? super ExecutionListener> action = actionCreator.createAction(parser, commandLine);
            if (action != null) {
                return action;
            }
        }
        return null;
    }
}
