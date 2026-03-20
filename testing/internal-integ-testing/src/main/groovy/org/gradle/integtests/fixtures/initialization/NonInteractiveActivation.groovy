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

package org.gradle.integtests.fixtures.initialization

import org.gradle.integtests.fixtures.executer.GradleExecuter

enum NonInteractiveActivation {
    ENVIRONMENT(["NONINTERACTIVE": "1"]),
    CLI("--non-interactive");

    Object value

    private NonInteractiveActivation(value) {
        this.value = value
    }

    @Override
    String toString() {
        return value.toString()
    }

    void applyTo(GradleExecuter executer) {
        switch (this) {
            case ENVIRONMENT:
                executer.withEnvironmentVars(value)
                break
            case CLI:
                executer.withArgument(value)
                break
            default:
                throw new Exception("Unknown non-interactive activation: $this")
        }
    }
}
