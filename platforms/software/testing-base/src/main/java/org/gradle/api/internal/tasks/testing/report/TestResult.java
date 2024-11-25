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
package org.gradle.api.internal.tasks.testing.report;

import org.gradle.api.internal.tasks.testing.junit.result.PersistentTestFailure;

import java.util.ArrayList;
import java.util.List;

import static org.gradle.api.tasks.testing.TestResult.ResultType;

public class TestResult extends TestResultModel implements Comparable<TestResult> {
    private final long duration;
    final ClassTestResults classResults;
    final List<PersistentTestFailure> failures = new ArrayList<PersistentTestFailure>();
    final String name;
    final String displayName;
    boolean ignored;

    public TestResult(String name, long duration, ClassTestResults classResults) {
        this(name, name, duration, classResults);
    }

    public TestResult(String name, String displayName, long duration, ClassTestResults classResults) {
        this.name = name;
        this.duration = duration;
        this.displayName = displayName;
        this.classResults = classResults;
    }

    public Object getId() {
        return name;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getTitle() {
        return "Test " + name;
    }

    @Override
    public ResultType getResultType() {
        if (ignored) {
            return ResultType.SKIPPED;
        }
        return failures.isEmpty() ? ResultType.SUCCESS : ResultType.FAILURE;
    }

    @Override
    public long getDuration() {
        return duration;
    }

    @Override
    public String getFormattedDuration() {
        return ignored ? "-" : super.getFormattedDuration();
    }

    public ClassTestResults getClassResults() {
        return classResults;
    }

    public List<PersistentTestFailure> getFailures() {
        return failures;
    }

    public boolean isIgnored() {
        return ignored;
    }

    public void addFailure(PersistentTestFailure failure) {
        classResults.failed(this);
        failures.add(failure);
    }

    public void setIgnored() {
        classResults.ignored(this);
        ignored = true;
    }

    @Override
    public int compareTo(TestResult testResult) {
        int diff = classResults.getName().compareTo(testResult.classResults.getName());
        if (diff != 0) {
            return diff;
        }

        diff = name.compareTo(testResult.name);
        if (diff != 0) {
            return diff;
        }

        Integer thisIdentity = System.identityHashCode(this);
        int otherIdentity = System.identityHashCode(testResult);
        return thisIdentity.compareTo(otherIdentity);
    }
}
