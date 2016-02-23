/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.performance.fixture;

import java.util.List;

public abstract class PerformanceTestResult {
    String testId;
    String jvm;
    String operatingSystem;
    long testTime;
    String vcsBranch;
    List<String> vcsCommits;
    List<String> previousTestIds;
    String versionUnderTest;

    public String getTestId() {
        return testId;
    }

    public void setTestId(String testId) {
        this.testId = testId;
    }

    public List<String> getPreviousTestIds() {
        return previousTestIds;
    }

    public void setPreviousTestIds(List<String> previousTestIds) {
        this.previousTestIds = previousTestIds;
    }

    public long getTestTime() {
        return testTime;
    }

    public void setTestTime(long testTime) {
        this.testTime = testTime;
    }

    public String getVersionUnderTest() {
        return versionUnderTest;
    }

    public void setVersionUnderTest(String versionUnderTest) {
        this.versionUnderTest = versionUnderTest;
    }

    public String getVcsBranch() {
        return vcsBranch;
    }

    public void setVcsBranch(String vcsBranch) {
        this.vcsBranch = vcsBranch;
    }

    public List<String> getVcsCommits() {
        return vcsCommits;
    }

    public void setVcsCommits(List<String> vcsCommits) {
        this.vcsCommits = vcsCommits;
    }

    public String getOperatingSystem() {
        return operatingSystem;
    }

    public void setOperatingSystem(String operatingSystem) {
        this.operatingSystem = operatingSystem;
    }

    public String getJvm() {
        return jvm;
    }

    public void setJvm(String jvm) {
        this.jvm = jvm;
    }

    public abstract void assertEveryBuildSucceeds();

}
