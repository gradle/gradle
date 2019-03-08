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
/**
 * A trait for testing console behavior.
 * <p>
 * <b>Note:</b> The console output contains formatting characters.
 */
trait RichConsoleStyling {
    public final static String CONTROL_SEQUENCE_START = "\u001B["

    static String workInProgressLine(String plainText) {
        return boldOn() + plainText + reset()
    }

    private static String boldOn() {
        "${CONTROL_SEQUENCE_START}1m"
    }

    private static String reset() {
        "${CONTROL_SEQUENCE_START}m"
    }
}
