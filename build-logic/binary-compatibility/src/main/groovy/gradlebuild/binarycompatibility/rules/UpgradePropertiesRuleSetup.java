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
import me.champeau.gradle.japicmp.report.SetupRule;
import me.champeau.gradle.japicmp.report.ViolationCheckContext;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static gradlebuild.binarycompatibility.upgrades.UpgradedProperties.CURRENT_METHODS_OF_UPGRADED_PROPERTIES;
import static gradlebuild.binarycompatibility.upgrades.UpgradedProperties.OLD_METHODS_OF_UPGRADED_PROPERTIES;
import static gradlebuild.binarycompatibility.upgrades.UpgradedProperties.SEEN_OLD_METHODS_OF_UPGRADED_PROPERTIES;
import static gradlebuild.binarycompatibility.upgrades.UpgradedProperties.getMethodKey;

public class UpgradePropertiesRuleSetup implements SetupRule {

    private static final String CURRENT_UPGRADED_PROPERTIES_KEY = "currentUpgradedProperties";
    private static final String BASE_UPGRADED_PROPERTIES_KEY = "baseUpgradedProperties";

    private final Map<String, String> params;

    public UpgradePropertiesRuleSetup(Map<String, String> params) {
        this.params = params;
    }

    @Override
    public void execute(ViolationCheckContext context) {
        List<UpgradedProperty> currentUpgradedProperties = UpgradedProperties.parse(params.get(CURRENT_UPGRADED_PROPERTIES_KEY));
        List<UpgradedProperty> baseUpgradedProperties = UpgradedProperties.parse(params.get(BASE_UPGRADED_PROPERTIES_KEY));
        context.putUserData(CURRENT_METHODS_OF_UPGRADED_PROPERTIES, diff(
            mapCurrentMethodsOfUpgradedProperties(currentUpgradedProperties),
            mapCurrentMethodsOfUpgradedProperties(baseUpgradedProperties)
        ));
        context.putUserData(OLD_METHODS_OF_UPGRADED_PROPERTIES, diff(
            mapOldMethodsOfUpgradedProperties(currentUpgradedProperties),
            mapOldMethodsOfUpgradedProperties(baseUpgradedProperties)
        ));
        context.putUserData(SEEN_OLD_METHODS_OF_UPGRADED_PROPERTIES, new HashSet<>());
    }

    private static Map<String, UpgradedProperty> mapCurrentMethodsOfUpgradedProperties(List<UpgradedProperty> upgradedProperties) {
        Map<String, UpgradedProperty> map = new HashMap<>();
        upgradedProperties.forEach(upgradedProperty -> {
            String key = getMethodKey(upgradedProperty.getContainingType(), upgradedProperty.getMethodName(), upgradedProperty.getMethodDescriptor());
            map.put(key, upgradedProperty);
        });
        return map;
    }

    private static Map<String, UpgradedProperty> mapOldMethodsOfUpgradedProperties(List<UpgradedProperty> upgradedProperties) {
        Map<String, UpgradedProperty> map = new HashMap<>();
        upgradedProperties.forEach(upgradedProperty -> map.putAll(mapOldMethodsOfUpgradedProperties(upgradedProperty)));
        return map;
    }

    private static Map<String, UpgradedProperty> mapOldMethodsOfUpgradedProperties(UpgradedProperty upgradedProperty) {
        Map<String, UpgradedProperty> map = new HashMap<>();
        upgradedProperty.getUpgradedMethods().forEach(upgradedMethod -> {
            String key = getMethodKey(upgradedProperty.getContainingType(), upgradedMethod.getName(), upgradedMethod.getDescriptor());
            map.put(key, upgradedProperty);
        });
        return map;
    }

    private static <T> Map<String, T> diff(Map<String, T> first, Map<String, T> second) {
        return first.entrySet().stream()
            .filter(e -> !second.containsKey(e.getKey()))
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
