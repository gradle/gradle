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

import org.gradle.internal.hash.Hasher;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ApiUpgradeReporter {
    public static final ApiUpgradeReporter NO_UPGRADES = new ApiUpgradeReporter(Collections.emptyList());

    private final List<ReportableApiChange> changes;

    public ApiUpgradeReporter(List<ReportableApiChange> changes) {
        this.changes = changes;
    }

    public List<String> reportApiChanges(int opcode, String owner, String name, String desc) {
        return changes.stream()
            .map(change -> change.reportApiChangeIfMatches(opcode, owner, name, desc))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .distinct()
            .collect(Collectors.toList());
    }

    public boolean shouldDecorateCallsiteArray() {
        return !changes.isEmpty();
    }

    public void applyConfigurationTo(Hasher hasher) {
        for (ReportableApiChange change : changes) {
            change.applyConfigurationTo(hasher);
        }
    }
}
