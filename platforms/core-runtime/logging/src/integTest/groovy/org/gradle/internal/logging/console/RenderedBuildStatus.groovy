/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.logging.console

/**
 * Reads the text after the progress bar from the console output: the phase label and the counts.
 * The duration and the flag for a grown total are left out, and spaces are collapsed.
 */
class RenderedBuildStatus {
    private static final String ANSI_SEQUENCE = /\u001B\[[0-9;?]*[A-Za-z]|\u001B\][^\u0007]*\u0007/

    static List<String> all(String output) {
        def plain = output.replaceAll(ANSI_SEQUENCE, '')
        def matcher = plain =~ /$ProgressBar.UNICODE_PROGRESS_BAR_PREFIX[^$ProgressBar.UNICODE_PROGRESS_BAR_SUFFIX]*$ProgressBar.UNICODE_PROGRESS_BAR_SUFFIX»?([^\[│>\r\n]*)/
        List<String> statuses = []
        while (matcher.find()) {
            statuses << matcher.group(1).replace('▲', '').replaceAll(/\s+/, ' ').trim()
        }
        return statuses
    }

    static String last(String output) {
        def statuses = all(output)
        return statuses.empty ? null : statuses.last()
    }
}
