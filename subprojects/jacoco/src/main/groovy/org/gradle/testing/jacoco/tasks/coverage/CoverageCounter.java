/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.testing.jacoco.tasks.coverage;

/**
 * A POJO that counts the number of covered and missed Jacoco observations.
 */
public class CoverageCounter {
    public final int covered;
    public final int missed;

    CoverageCounter(int covered, int missed) {
        this.covered = covered;
        this.missed = missed;
    }

    @Override
    public String toString() {
        return String.format("Covered: %d, Missed: %d", covered, missed);
    }
}
