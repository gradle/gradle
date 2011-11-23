/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.util

import org.junit.rules.TestRule
import org.junit.runners.model.Statement
import org.junit.runner.Description

class PreconditionVerifier implements TestRule {
    Statement apply(Statement base, Description description) {
        def preconditions = description.annotations.findAll { it instanceof Requires }*.value().flatten()
        preconditions.every { it.fulfilled } ? base : EmptyStatement.INSTANCE
    }
}
