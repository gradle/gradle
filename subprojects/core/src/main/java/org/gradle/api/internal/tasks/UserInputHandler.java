/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.api.internal.tasks;

import org.apache.commons.lang.StringUtils;
import org.gradle.api.UncheckedIOException;
import org.gradle.internal.logging.events.UserInputRequestEvent;
import org.gradle.internal.logging.events.UserInputResumeEvent;
import org.gradle.internal.logging.sink.OutputEventRenderer;

import java.util.Scanner;

// TODO:DAZ Sanity check input
// TODO:DAZ Handle ctrl-D to cancel build during input
public class UserInputHandler {
    private final OutputEventRenderer outputEventRenderer;
    private final Scanner scanner = new Scanner(System.in);

    public UserInputHandler(OutputEventRenderer outputEventRenderer) {
        this.outputEventRenderer = outputEventRenderer;
    }

    public String getUserResponse(String prompt) {
        outputEventRenderer.onOutput(new UserInputRequestEvent(prompt));
        try {
            return StringUtils.trim(readInput());
        } finally {
            outputEventRenderer.onOutput(new UserInputResumeEvent());
        }
    }

    private String readInput() {
        if (!isUserInputSupported()) {
            throw new UncheckedIOException("Console does not support capturing input");
        }

        return scanner.nextLine();
    }

    public boolean isUserInputSupported() {
        return scanner.hasNextLine();
    }
}
