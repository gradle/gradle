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

import gradlebuild.binarycompatibility.upgrades.UpgradedProperties;
import gradlebuild.binarycompatibility.upgrades.UpgradedProperty;
import gradlebuild.binarycompatibility.upgrades.UpgradedProperty.ReplacedAccessor;
import gradlebuild.binarycompatibility.upgrades.UpgradedProperty.AccessorKey;
import me.champeau.gradle.japicmp.report.SetupRule;
import me.champeau.gradle.japicmp.report.ViolationCheckContext;

import java.util.AbstractMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static gradlebuild.binarycompatibility.upgrades.UpgradedProperties.CURRENT_ACCESSORS_OF_UPGRADED_PROPERTIES;
import static gradlebuild.binarycompatibility.upgrades.UpgradedProperties.OLD_REMOVED_ACCESSORS_OF_UPGRADED_PROPERTIES;
import static gradlebuild.binarycompatibility.upgrades.UpgradedProperties.SEEN_OLD_REMOVED_ACCESSORS_OF_UPGRADED_PROPERTIES;
import static gradlebuild.binarycompatibility.upgrades.UpgradedProperty.BinaryCompatibility.ACCESSORS_REMOVED;

public class UpgradePropertiesRuleSetup implements SetupRule {

    private static final String CURRENT_UPGRADED_PROPERTIES_KEY = "currentUpgradedProperties";
    private static final String BASELINE_UPGRADED_PROPERTIES_KEY = "baselineUpgradedProperties";

    private final Map<String, String> params;

    public UpgradePropertiesRuleSetup(Map<String, String> params) {
        this.params = params;
    }

    @Override
    public void execute(ViolationCheckContext context) {
        List<UpgradedProperty> currentUpgradedProperties = UpgradedProperties.parse(params.get(CURRENT_UPGRADED_PROPERTIES_KEY));
        List<UpgradedProperty> baselineUpgradedProperties = UpgradedProperties.parse(params.get(BASELINE_UPGRADED_PROPERTIES_KEY));
        context.putUserData(CURRENT_ACCESSORS_OF_UPGRADED_PROPERTIES, diff(
            currentAccessorsOfUpgradedProperties(currentUpgradedProperties),
            currentAccessorsOfUpgradedProperties(baselineUpgradedProperties)
        ));
        context.putUserData(OLD_REMOVED_ACCESSORS_OF_UPGRADED_PROPERTIES, diff(
            oldRemovedAccessorsOfUpgradedProperties(currentUpgradedProperties),
            oldRemovedAccessorsOfUpgradedProperties(baselineUpgradedProperties)
        ));
        context.putUserData(SEEN_OLD_REMOVED_ACCESSORS_OF_UPGRADED_PROPERTIES, new HashSet<>());
    }

    private static Map<AccessorKey, UpgradedProperty> currentAccessorsOfUpgradedProperties(List<UpgradedProperty> upgradedProperties) {
        return upgradedProperties.stream().collect(Collectors.toMap(AccessorKey::ofUpgradedProperty, Function.identity()));
    }

    private static Map<AccessorKey, ReplacedAccessor> oldRemovedAccessorsOfUpgradedProperties(List<UpgradedProperty> upgradedProperties) {
        return upgradedProperties.stream()
            .flatMap(UpgradePropertiesRuleSetup::oldRemovedAccessorsOfUpgradedProperty)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static Stream<Map.Entry<AccessorKey, ReplacedAccessor>> oldRemovedAccessorsOfUpgradedProperty(UpgradedProperty upgradedProperty) {
        return upgradedProperty.getReplacedAccessors().stream()
            .filter(replacedAccessor -> replacedAccessor.getBinaryCompatibility() == ACCESSORS_REMOVED)
            .map(replacedAccessor -> {
                AccessorKey key = AccessorKey.ofReplacedAccessor(upgradedProperty.getContainingType(), replacedAccessor);
                return new AbstractMap.SimpleEntry<>(key, replacedAccessor);
            });
    }

    private static <T> Map<AccessorKey, T> diff(Map<AccessorKey, T> first, Map<AccessorKey, T> second) {
        return first.entrySet().stream()
            .filter(e -> !second.containsKey(e.getKey()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
