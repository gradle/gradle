/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.integtests.fixtures.executer

import java.util.regex.Pattern

import static org.gradle.util.Matchers.*;
import org.hamcrest.Matcher

public class DependencyResolutionFailure {
    private final ExecutionFailure failure

    DependencyResolutionFailure(ExecutionFailure failure, String configuration) {
        this.failure = failure
        assertFailedConfiguration(configuration);
    }

    DependencyResolutionFailure assertFailedConfiguration(String configuration) {
        failure.assertThatCause(matchesRegexp("Could not resolve all (dependencies|artifacts|files) for configuration '${Pattern.quote(configuration)}'."))
        this
    }

    DependencyResolutionFailure assertFailedDependencyRequiredBy(String dependency) {
        failure.assertThatCause(matchesRegexp("(?ms).*Required by:\\s+$dependency.*"))
        this
    }

    DependencyResolutionFailure assertHasCause(String cause) {
        failure.assertHasCause(cause)
        this
    }

    DependencyResolutionFailure assertThatCause(Matcher<String> matcher) {
        failure.assertThatCause(matcher)
        this
    }
}
