/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.tasks.testing;

import org.gradle.api.tasks.testing.TestFailure;
import org.gradle.api.tasks.testing.TestFailureDetails;

import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

@org.gradle.api.NonNullApi
public class AssumptionTestFailure extends TestFailure {

    private final Throwable rawFailure;

    public AssumptionTestFailure(Throwable rawFailure) {
        this.rawFailure = rawFailure;
    }

    @Override
    public List<TestFailure> getCauses() {
        return Collections.<TestFailure>emptyList();
    }

    @Override
    public Throwable getRawFailure() {
        return rawFailure;
    }

    @Override
    @Nullable
    public TestFailureDetails getDetails() {
        return null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        AssumptionTestFailure that = (AssumptionTestFailure) o;

        return rawFailure != null ? rawFailure.equals(that.rawFailure) : that.rawFailure == null;
    }

    @Override
    public int hashCode() {
        return rawFailure != null ? rawFailure.hashCode() : 0;
    }

    public static TestFailure fromAssumptionFailure(Throwable failure) {
        return new AssumptionTestFailure(failure);
    }
}
