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

package org.gradle.integtests.fixtures

import org.gradle.integtests.fixtures.executer.GradleHandle
import org.gradle.integtests.fixtures.executer.LogContent

/**
 * A trait for testing console behavior.
 * <p>
 * <b>Note:</b> The console output contains formatting characters.
 */
trait RichConsoleStyling {
    static String workInProgressLine(String plainText) {
        return "{bold-on}" + plainText + "{bold-off}"
    }

    static void assertHasWorkInProgress(GradleHandle handle, String plainText) {
        assert LogContent.of(handle.standardOutput).ansiCharsToColorText().withNormalizedEol().contains(workInProgressLine(plainText))
    }
}
