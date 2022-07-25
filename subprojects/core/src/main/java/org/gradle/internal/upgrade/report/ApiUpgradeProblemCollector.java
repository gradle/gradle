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

package org.gradle.internal.upgrade.report;

import org.gradle.StartParameter;
import org.gradle.internal.hash.Hasher;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.internal.upgrade.report.ReportableApiChange.ApiMatcher;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@ServiceScope(Scopes.BuildSession.class)
public class ApiUpgradeProblemCollector {

    private static final String BINARY_UPGRADE_JSON_PATH = "org.gradle.binary.upgrade.report.json";

    private final List<ReportableApiChange> apiUpgrades;
    private final Map<ApiMatcher, Set<ReportableApiChange>> apiUpgradeMap;
    private final Set<String> detectedProblems;
    private final String randomHash;

    @Inject
    public ApiUpgradeProblemCollector(@Nullable StartParameter startParameter) {
        this.apiUpgrades = getApiUpgradesFromFile(startParameter);
        this.apiUpgradeMap = toMap(apiUpgrades);
        this.detectedProblems = ConcurrentHashMap.newKeySet();
        this.randomHash = UUID.randomUUID().toString();
        DynamicGroovyApiUpgradeDecorator.init(this);
    }

    private static List<ReportableApiChange> getApiUpgradesFromFile(@Nullable StartParameter startParameter) {
        String path = startParameter != null
            ? startParameter.getSystemPropertiesArgs().get(BINARY_UPGRADE_JSON_PATH)
            : null;
        if (path == null || !Files.exists(Paths.get(path))) {
            return Collections.emptyList();
        }
        File file = new File(path);
        return new ApiUpgradeJsonParser().parseAcceptedApiChanges(file);
    }

    private Map<ApiMatcher, Set<ReportableApiChange>> toMap(List<ReportableApiChange> changes) {
        Map<ApiMatcher, Set<ReportableApiChange>> map = new LinkedHashMap<>();
        for (ReportableApiChange change : changes) {
            for (ApiMatcher matcher : change.getMatchers()) {
                map.computeIfAbsent(matcher, k -> new LinkedHashSet<>()).add(change);
            }
        }
        return map;
    }

    public void collectStaticApiChangesReport(int opcode, String sourceFile, int lineNumber, String owner, String name, String desc) {
        ApiMatcher matcher = new ApiMatcher(opcode, owner, name, desc);
        List<String> report = apiUpgradeMap.getOrDefault(matcher, Collections.emptySet()).stream()
            .map(ReportableApiChange::getApiChangeReport)
            .collect(Collectors.toList());
        if (!report.isEmpty()) {
            // Report just first issue for now
            detectedProblems.add(String.format("%s: line: %s: %s", sourceFile, lineNumber, report.get(0)));
        }
    }

    public void collectDynamicApiChangesReport(String sourceFile, int lineNumber, String report) {
        detectedProblems.add(String.format("%s: line: %s: %s", sourceFile, lineNumber, report));
    }

    public List<ReportableApiChange> getApiUpgrades() {
        return apiUpgrades;
    }

    public Set<String> getDetectedProblems() {
        return detectedProblems;
    }

    public void applyConfigurationTo(Hasher hasher) {
        if (!apiUpgradeMap.isEmpty()) {
            // This invalidates transform cache, so report is shown always, this is good for a spike,
            // but should be done differently for production
            hasher.putString(randomHash);
        }
    }

    public static ApiUpgradeProblemCollector noUpgrades() {
        return new ApiUpgradeProblemCollector(null);
    }
}
