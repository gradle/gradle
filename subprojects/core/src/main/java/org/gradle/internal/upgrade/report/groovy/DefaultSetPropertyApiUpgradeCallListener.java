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

package org.gradle.internal.upgrade.report.groovy;

import org.gradle.internal.lazy.Lazy;
import org.gradle.internal.metaobject.BeanDynamicObject;
import org.gradle.internal.upgrade.report.ApiUpgradeUtils;
import org.gradle.internal.upgrade.report.config.ApiUpgradeConfig;
import org.gradle.internal.upgrade.report.config.ReportableApiUpgrade;
import org.gradle.internal.upgrade.report.problems.ApiUpgradeProblemCollector;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.gradle.internal.upgrade.report.ApiUpgradeUtils.getStackTraceElementForAnyFile;

public class DefaultSetPropertyApiUpgradeCallListener implements BeanDynamicObject.BeanDynamicObjectCallListener {

    private final ApiUpgradeProblemCollector collector;
    private final Lazy<Map<String, Set<ReportableApiUpgrade>>> setters;

    public DefaultSetPropertyApiUpgradeCallListener(ApiUpgradeProblemCollector collector, ApiUpgradeConfig config) {
        this.collector = collector;
        this.setters = Lazy.locking().of(() -> initSetterToReportableChange(config));
    }

    private Map<String, Set<ReportableApiUpgrade>> initSetterToReportableChange(ApiUpgradeConfig config) {
        Map<String, Set<ReportableApiUpgrade>> map = new LinkedHashMap<>();
        config.getApiUpgrades().stream()
            .filter(change -> change.getId().getName().startsWith("set"))
            .forEach(change -> {
                String propertyName = change.getId().getName().replace("set", "");
                propertyName = propertyName.substring(0, 1).toLowerCase() + propertyName.substring(1);
                map.computeIfAbsent(propertyName, k -> new LinkedHashSet<>()).add(change);
            });
        return map;
    }

    @Override
    public void onSetProperty(Object bean, String propertyName, Object value) {
        Set<ReportableApiUpgrade> upgrades = setters.get().getOrDefault(propertyName, Collections.emptySet());
        if (upgrades.isEmpty()) {
            return;
        }
        Optional<ReportableApiUpgrade> matchingChange = upgrades.stream()
            .filter(change -> ApiUpgradeUtils.isInstanceOfType(change.getId().getOwner(), bean))
            .findFirst();
        if (matchingChange.isPresent()) {
            Optional<StackTraceElement> ownerStacktrace = getStackTraceElementForAnyFile();
            String file = ownerStacktrace.map(StackTraceElement::getFileName).orElse("<Unknown file>");
            int lineNumber = ownerStacktrace.map(StackTraceElement::getLineNumber).orElse(-1);
            collector.collectDynamicApiChangesReport(file, lineNumber, matchingChange.get().getApiUpgradeProblem());
        }
    }
}
