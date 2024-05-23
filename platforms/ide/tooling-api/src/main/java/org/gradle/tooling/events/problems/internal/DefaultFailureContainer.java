/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.tooling.events.problems.internal;

import org.gradle.tooling.Failure;
import org.gradle.tooling.events.problems.FailureContainer;

import javax.annotation.Nullable;
import java.util.Objects;

public class DefaultFailureContainer implements FailureContainer {
    private final Failure failure;

    public DefaultFailureContainer(@Nullable Failure failure) {
        this.failure = failure;
    }

    @Override
    public Failure getFailure() {
        return failure;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultFailureContainer that = (DefaultFailureContainer) o;
        return Objects.equals(failure, that.failure);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(failure);
    }
}
