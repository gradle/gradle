/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.integtests.fixtures.executer;

public enum ConsoleAttachment {
    NOT_ATTACHED("not attached to a console", false, false),
    ATTACHED("console attached to both stdout and stderr", true, true),
    ATTACHED_STDOUT_ONLY("console attached to stdout only", true, false);

    private final String description;
    private final boolean consoleAttached;
    private final boolean stderrAttached;

    ConsoleAttachment(String description, boolean consoleAttached, boolean stderrAttached) {
        this.description = description;
        this.consoleAttached = consoleAttached;
        this.stderrAttached = stderrAttached;
    }

    public String getDescription() {
        return description;
    }

    public boolean isConsoleAttached() {
        return consoleAttached;
    }

    public boolean isStderrAttached() {
        return stderrAttached;
    }
}
