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

import org.gradle.internal.nativeintegration.console.TestConsoleMetadata;

public enum ConsoleAttachment {
    NOT_ATTACHED("not attached to a console", null),
    ATTACHED("console attached to both stdout and stderr", TestConsoleMetadata.BOTH),
    ATTACHED_STDOUT_ONLY("console attached to stdout only", TestConsoleMetadata.STDOUT_ONLY),
    ATTACHED_STDERR_ONLY("console attached to stderr only", TestConsoleMetadata.STDERR_ONLY);

    private final String description;
    TestConsoleMetadata consoleMetaData;

    ConsoleAttachment(String description, TestConsoleMetadata consoleMetaData) {
        this.description = description;
        this.consoleMetaData = consoleMetaData;
    }

    public String getDescription() {
        return description;
    }

    public boolean isStderrAttached() {
        return consoleMetaData != null && consoleMetaData.isStdErr();
    }

    public boolean isStdoutAttached() {
        return consoleMetaData != null && consoleMetaData.isStdOut();
    }

    public TestConsoleMetadata getConsoleMetaData() {
        return consoleMetaData;
    }
}
