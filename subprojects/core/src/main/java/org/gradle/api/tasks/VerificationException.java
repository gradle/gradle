/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.tasks;

import org.gradle.api.GradleException;
import org.gradle.api.Incubating;

/**
 * Signals that tests have failed. This is only thrown when tests are executed and some tests have failed execution.
 *
 * @since 7.4
 */
@Incubating
public class VerificationException extends GradleException {
    public VerificationException(String message) {
        super(message);
    }
}
