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

import gradlebuild.binarycompatibility.upgrades.UpgradedProperty;
import gradlebuild.binarycompatibility.upgrades.UpgradedProperty.MethodKey;
import me.champeau.gradle.japicmp.report.PostProcessViolationsRule;
import me.champeau.gradle.japicmp.report.ViolationCheckContextWithViolations;
import org.gradle.util.internal.CollectionUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static gradlebuild.binarycompatibility.upgrades.UpgradedProperties.OLD_METHODS_OF_UPGRADED_PROPERTIES;
import static gradlebuild.binarycompatibility.upgrades.UpgradedProperties.SEEN_OLD_METHODS_OF_UPGRADED_PROPERTIES;

public class UpgradePropertiesRulePostProcess implements PostProcessViolationsRule {

    @Override
    @SuppressWarnings("unchecked")
    public void execute(ViolationCheckContextWithViolations context) {
        Set<MethodKey> seenUpgradedMethodChanges = (Set<MethodKey>) context.getUserData().get(SEEN_OLD_METHODS_OF_UPGRADED_PROPERTIES);
        Map<MethodKey, UpgradedProperty> oldMethodsOfUpgradedProperties = (Map<MethodKey, UpgradedProperty>) context.getUserData().get(OLD_METHODS_OF_UPGRADED_PROPERTIES);
        Map<MethodKey, UpgradedProperty> left = new HashMap<>(oldMethodsOfUpgradedProperties);
        left.keySet().removeIf(seenUpgradedMethodChanges::contains);
        if (!left.isEmpty()) {
            String formattedLeft = CollectionUtils.join("\n", left.keySet());
            throw new RuntimeException("The following methods were upgraded, but didn't match any changed method:\n\n" + formattedLeft);
        }
    }
}
