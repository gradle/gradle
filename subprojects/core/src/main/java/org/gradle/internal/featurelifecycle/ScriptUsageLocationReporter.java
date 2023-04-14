/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.internal.featurelifecycle;

import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.problems.Location;
import org.gradle.problems.buildtree.ProblemLocationAnalyzer;

import javax.annotation.concurrent.ThreadSafe;

@ServiceScope(Scopes.BuildTree.class)
@ThreadSafe
public class ScriptUsageLocationReporter implements UsageLocationReporter {
    private final ProblemLocationAnalyzer locationAnalyzer;

    public ScriptUsageLocationReporter(ProblemLocationAnalyzer locationAnalyzer) {
        this.locationAnalyzer = locationAnalyzer;
    }

    @Override
    public void reportLocation(FeatureUsage usage, StringBuilder target) {
        Location location = locationAnalyzer.locationForUsage(usage.getStack(), false);
        if (location != null) {
            target.append(location.getFormatted());
        }
    }
}
