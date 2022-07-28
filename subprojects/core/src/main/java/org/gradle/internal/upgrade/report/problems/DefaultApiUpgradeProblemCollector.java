/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.upgrade.report.problems;

import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Multimap;
import org.gradle.internal.upgrade.report.config.ApiUpgradeConfig;
import org.gradle.internal.upgrade.report.config.ReportableApiUpgrade;
import org.gradle.internal.upgrade.report.config.ReportableApiUpgrade.ApiUpgradeId;

import javax.inject.Inject;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class DefaultApiUpgradeProblemCollector implements ApiUpgradeProblemCollector {
    private final Multimap<ApiUpgradeId, ReportableApiUpgrade> apiUpgradeMap;
    private final Set<String> detectedProblems;

    @Inject
    public DefaultApiUpgradeProblemCollector(ApiUpgradeConfig config) {
        this.apiUpgradeMap = toMap(config.getApiUpgrades());
        this.detectedProblems = ConcurrentHashMap.newKeySet();
    }

    private Multimap<ApiUpgradeId, ReportableApiUpgrade> toMap(List<ReportableApiUpgrade> changes) {
        ImmutableSetMultimap.Builder<ApiUpgradeId, ReportableApiUpgrade> map = ImmutableSetMultimap.builder();
        for (ReportableApiUpgrade change : changes) {
            for (ApiUpgradeId matcher : change.getAllKnownTypeIds()) {
                map.put(matcher, change);
            }
        }
        return map.build();
    }

    public void collectStaticApiChangesReport(int opcode, String sourceFile, int lineNumber, String owner, String name, String desc) {
        ApiUpgradeId matcher = new ApiUpgradeId(opcode, owner, name, desc);
        List<String> report = apiUpgradeMap.get(matcher).stream()
            .map(ReportableApiUpgrade::getApiUpgradeProblem)
            .collect(Collectors.toList());
        if (!report.isEmpty()) {
            // Report just first issue for now
            detectedProblems.add(String.format("%s: line: %s: %s", sourceFile, lineNumber, report.get(0)));
        }
    }

    public void collectDynamicApiChangesReport(String sourceFile, int lineNumber, String report) {
        detectedProblems.add(String.format("%s: line: %s: %s", sourceFile, lineNumber, report));
    }

    public Set<String> getDetectedProblems() {
        return detectedProblems;
    }
}
