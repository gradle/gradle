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

package org.gradle.performance.results;

public class CrossBuildResultsStore extends BaseCrossBuildResultsStore<CrossBuildPerformanceResults> {

    public CrossBuildResultsStore() {
        super("cross-build-results");
    }

    @Override
    protected boolean ignore(CrossBuildPerformanceResults performanceResults) {
        return performanceResults.getVcsCommits().get(0).equals("be4e537ebdaab43fd1dae5c4b1d52a56987f5be2")
            || performanceResults.getVcsCommits().get(0).equals("508ccbeb7633413609bd3be205c40f30a8c5f2bb")
            || performanceResults.getVcsCommits().get(0).equals("fdd431387993e1d7e4d6d3aec31a43ec4b533567")
            || performanceResults.getTestId().contains("build receipt");
    }

}
