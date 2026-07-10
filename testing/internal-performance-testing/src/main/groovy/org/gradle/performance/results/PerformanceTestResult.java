/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.performance.results;

import java.util.Arrays;
import java.util.List;

public abstract class PerformanceTestResult {
    private String testId;
    private String testClass;
    private String testProject;
    private String teamCityBuildId;
    private String jvm;
    private String operatingSystem;
    private String host;
    private long startTime;
    private long endTime;
    private String vcsBranch;
    private List<String> vcsCommits;
    private List<String> previousTestIds;
    private String versionUnderTest;
    private String channel;

    /**
     * Returns true if regression checks is enabled.
     *
     * When checks is enabled, an exception is thrown upon the performance test regression.
     * Otherwise the regression is ignored.
     *
     * @return true if regression checks enabled, false otherwise.
     */
    public static boolean hasRegressionChecks() {
        String check = System.getProperty("org.gradle.performance.regression.checks", "true");
        return Arrays.asList("true", "all").contains(check);
    }

    public PerformanceExperiment getPerformanceExperiment() {
        return new PerformanceExperiment(testProject, new PerformanceScenario(testClass, testId));
    }

    public String getTestClass() {
        return testClass;
    }

    public void setTestClass(String testClass) {
        this.testClass = testClass;
    }

    public String getTestId() {
        return testId;
    }

    public void setTestId(String testId) {
        this.testId = testId;
    }

    public String getTestProject() {
        return testProject;
    }

    public void setTestProject(String testProject) {
        this.testProject = testProject;
    }

    public String getTeamCityBuildId() {
        return teamCityBuildId;
    }

    public void setTeamCityBuildId(String teamCityBuildId) {
        this.teamCityBuildId = teamCityBuildId;
    }

    public List<String> getPreviousTestIds() {
        return previousTestIds;
    }

    public void setPreviousTestIds(List<String> previousTestIds) {
        this.previousTestIds = previousTestIds;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
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

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }
}
