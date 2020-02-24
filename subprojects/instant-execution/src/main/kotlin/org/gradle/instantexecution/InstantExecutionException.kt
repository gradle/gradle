/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.instantexecution

import org.gradle.api.GradleException


abstract class InstantExecutionException(message: String) : GradleException(message)


/**
 * Instant execution state could not be cached.
 */
class InstantExecutionErrorsException : InstantExecutionException(
    "Instant execution state could not be cached."
)


class TooManyInstantExecutionProblemsException : InstantExecutionException(
    "Maximum number of instant execution problems has been reached. This behavior can be adjusted via -D${SystemProperties.maxProblems}=<integer>."
)


class InstantExecutionProblemsException : InstantExecutionException(
    "Problems found while caching instant execution state. Failing because -D${SystemProperties.failOnProblems} is 'true'."
)
