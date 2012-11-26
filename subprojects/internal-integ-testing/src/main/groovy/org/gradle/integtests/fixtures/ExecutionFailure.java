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
package org.gradle.integtests.fixtures;

import org.hamcrest.Matcher;

public interface ExecutionFailure extends ExecutionResult {
    ExecutionFailure assertHasLineNumber(int lineNumber);

    ExecutionFailure assertHasFileName(String filename);

    ExecutionFailure assertHasCause(String description);

    ExecutionFailure assertThatCause(Matcher<String> matcher);

    ExecutionFailure assertHasDescription(String context);

    ExecutionFailure assertThatDescription(Matcher<String> matcher);

    ExecutionFailure assertHasNoCause();

    ExecutionFailure assertTestsFailed();
}
