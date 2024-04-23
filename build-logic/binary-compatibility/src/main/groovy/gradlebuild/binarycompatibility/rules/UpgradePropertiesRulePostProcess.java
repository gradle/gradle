/*
 * Copyright 2023 the original author or authors.
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

package gradlebuild.binarycompatibility.rules;

import gradlebuild.binarycompatibility.upgrades.UpgradedProperty.BinaryCompatibility;
import gradlebuild.binarycompatibility.upgrades.UpgradedProperty.ReplacedAccessor;
import gradlebuild.binarycompatibility.upgrades.UpgradedProperty.AccessorKey;
import me.champeau.gradle.japicmp.report.PostProcessViolationsRule;
import me.champeau.gradle.japicmp.report.ViolationCheckContextWithViolations;
import org.gradle.util.internal.CollectionUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static gradlebuild.binarycompatibility.upgrades.UpgradedProperties.OLD_ACCESSORS_OF_UPGRADED_PROPERTIES;
import static gradlebuild.binarycompatibility.upgrades.UpgradedProperties.SEEN_OLD_ACCESSORS_OF_UPGRADED_PROPERTIES;

public class UpgradePropertiesRulePostProcess implements PostProcessViolationsRule {

    @Override
    @SuppressWarnings("unchecked")
    public void execute(ViolationCheckContextWithViolations context) {
        Set<AccessorKey> seenUpgradedAccessorsChanges = (Set<AccessorKey>) context.getUserData().get(SEEN_OLD_ACCESSORS_OF_UPGRADED_PROPERTIES);
        Map<AccessorKey, ReplacedAccessor> oldAccessorsOfUpgradedProperties = (Map<AccessorKey, ReplacedAccessor>) context.getUserData().get(OLD_ACCESSORS_OF_UPGRADED_PROPERTIES);

        // Find accessors that were not removed but should be
        Map<AccessorKey, ReplacedAccessor> keptAccessors = new HashMap<>(oldAccessorsOfUpgradedProperties);
        keptAccessors.entrySet().removeIf(e -> {
            if (seenUpgradedAccessorsChanges.contains(e.getKey())) {
                return true;
            }
            ReplacedAccessor accessor = e.getValue();
            return accessor.getBinaryCompatibility() == BinaryCompatibility.ACCESSORS_KEPT;
        });
        if (!keptAccessors.isEmpty()) {
            String formattedLeft = CollectionUtils.join("\n", keptAccessors.keySet());
            throw new RuntimeException("The following accessors were upgraded, but didn't match any removed/changed method:\n\n" + formattedLeft);
        }

        // Find accessors that were removed but shouldn't be
        Map<AccessorKey, ReplacedAccessor> removedAccessors = new HashMap<>(oldAccessorsOfUpgradedProperties);
        removedAccessors.entrySet().removeIf(e -> {
            if (!seenUpgradedAccessorsChanges.contains(e.getKey())) {
                return true;
            }
            ReplacedAccessor accessor = e.getValue();
            return accessor.getBinaryCompatibility() == BinaryCompatibility.ACCESSORS_REMOVED;
        });
        if (!removedAccessors.isEmpty()) {
            String formattedKept = CollectionUtils.join("\n", keptAccessors.keySet());
            throw new RuntimeException("The following accessors were upgraded, but methods were removed although they shouldn't be:\n\n" + formattedKept);
        }
    }
}
