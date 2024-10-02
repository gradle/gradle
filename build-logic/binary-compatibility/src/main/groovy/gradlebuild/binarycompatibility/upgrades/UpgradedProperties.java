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

package gradlebuild.binarycompatibility.upgrades;

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import gradlebuild.binarycompatibility.upgrades.UpgradedProperty.AccessorKey;
import gradlebuild.binarycompatibility.upgrades.UpgradedProperty.ReplacedAccessor;
import japicmp.model.JApiCompatibility;
import japicmp.model.JApiMethod;
import me.champeau.gradle.japicmp.report.Violation;
import me.champeau.gradle.japicmp.report.ViolationCheckContext;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import static gradlebuild.binarycompatibility.rules.SinceAnnotationMissingRule.SINCE_ERROR_MESSAGE;
import static japicmp.model.JApiCompatibilityChange.METHOD_ADDED_TO_INTERFACE;
import static japicmp.model.JApiCompatibilityChange.METHOD_ADDED_TO_PUBLIC_CLASS;
import static japicmp.model.JApiCompatibilityChange.METHOD_NEW_DEFAULT;
import static japicmp.model.JApiCompatibilityChange.METHOD_REMOVED;
import static japicmp.model.JApiCompatibilityChange.METHOD_RETURN_TYPE_CHANGED;

public class UpgradedProperties {

    private static final Pattern SETTER_REGEX = Pattern.compile("set[A-Z].*");
    private static final Pattern GETTER_REGEX = Pattern.compile("get[A-Z].*");
    private static final Pattern BOOLEAN_GETTER_REGEX = Pattern.compile("is[A-Z].*");
    public static final String OLD_REMOVED_ACCESSORS_OF_UPGRADED_PROPERTIES = "oldRemovedAccessorsOfUpgradedProperties";
    public static final String SEEN_OLD_REMOVED_ACCESSORS_OF_UPGRADED_PROPERTIES = "seenOldRemovedAccessorsOfUpgradedProperties";
    public static final String CURRENT_ACCESSORS_OF_UPGRADED_PROPERTIES = "currentAccessorsOfUpgradedProperties";

    public static List<UpgradedProperty> parse(String path) {
        File file = new File(path);
        if (!file.exists()) {
            return Collections.emptyList();
        }
        try (FileReader reader = new FileReader(file)) {
            List<UpgradedProperty> upgradedProperties = new Gson().fromJson(reader, new TypeToken<List<UpgradedProperty>>() {}.getType());
            // FIXME There should be no duplicates, yet there are some
            return upgradedProperties.stream()
                .distinct()
                .collect(ImmutableList.toImmutableList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Automatically accept changes that are valid property upgrades of a getter or setter.
     *
     * Here we automatically accept the following cases:
     * - A setter `setX` of an upgraded property is removed
     * - A boolean `isX` of an upgraded property is removed
     * - A new getter `getX` is added, where the old getter is a boolean getter `isX` of an upgraded property
     * - A return type is changed for a getter `getX` of an upgraded property
     *
     * We don't automatically accept changes when the @since annotation is missing, because we want to keep this information on the API.
     */
    public static boolean shouldAcceptForUpgradedProperty(JApiMethod jApiMethod, Violation violation, ViolationCheckContext context) {
        Map<AccessorKey, UpgradedProperty> currentAccessors = context.getUserData(CURRENT_ACCESSORS_OF_UPGRADED_PROPERTIES);
        Map<AccessorKey, ReplacedAccessor> oldRemovedAccessors = context.getUserData(OLD_REMOVED_ACCESSORS_OF_UPGRADED_PROPERTIES);

        if (violation.getHumanExplanation().startsWith(SINCE_ERROR_MESSAGE)) {
            // We want to keep @since nagging for new methods, unless it's `getX` or `getIsX` method that replaces `isX` boolean method.
            return isCurrentGetterThatReplacesBooleanIsGetter(jApiMethod, currentAccessors) || isKotlinBooleanSourceCompatibilityMethod(jApiMethod, currentAccessors);
        }

        if (jApiMethod.getCompatibilityChanges().contains(METHOD_NEW_DEFAULT) && isKotlinBooleanSourceCompatibilityMethod(jApiMethod, currentAccessors)) {
            // Accept also default `getIsX` methods added to interface that are for Kotlin source compatibility
            return true;
        }

        if (jApiMethod.getCompatibilityChanges().contains(METHOD_REMOVED)) {
            return isOldSetterOfUpgradedProperty(jApiMethod, oldRemovedAccessors) || isOldGetterOfUpgradedProperty(jApiMethod, oldRemovedAccessors) || isOldBooleanGetterOfUpgradedProperty(jApiMethod, oldRemovedAccessors);
        } else if (jApiMethod.getCompatibilityChanges().contains(METHOD_ADDED_TO_PUBLIC_CLASS) || jApiMethod.getCompatibilityChanges().contains(METHOD_ADDED_TO_INTERFACE)) {
            return isCurrentGetterOfUpgradedProperty(jApiMethod, currentAccessors) || isKotlinBooleanSourceCompatibilityMethod(jApiMethod, currentAccessors);
        } else if (jApiMethod.getCompatibilityChanges().contains(METHOD_RETURN_TYPE_CHANGED)) {
            return isCurrentGetterOfUpgradedProperty(jApiMethod, currentAccessors) && isOldGetterOfUpgradedProperty(jApiMethod, oldRemovedAccessors);
        }

        return false;
    }

    private static boolean isOldSetterOfUpgradedProperty(JApiMethod jApiMethod, Map<AccessorKey, ReplacedAccessor> upgradedMethods) {
        return isOldMethod(jApiMethod, upgradedMethods, SETTER_REGEX);
    }

    private static boolean isOldGetterOfUpgradedProperty(JApiMethod jApiMethod, Map<AccessorKey, ReplacedAccessor> upgradedMethods) {
        return isOldMethod(jApiMethod, upgradedMethods, GETTER_REGEX);
    }

    private static boolean isOldBooleanGetterOfUpgradedProperty(JApiMethod jApiMethod, Map<AccessorKey, ReplacedAccessor> upgradedMethods) {
        return isOldMethod(jApiMethod, upgradedMethods, BOOLEAN_GETTER_REGEX) && jApiMethod.getReturnType().getOldReturnType().equals("boolean");
    }

    private static boolean isKotlinBooleanSourceCompatibilityMethod(JApiMethod jApiMethod, Map<AccessorKey, UpgradedProperty> currentMethods) {
        if (!jApiMethod.getName().startsWith("getIs")) {
            return false;
        }
        String methodName = "get" + jApiMethod.getName().substring(5);
        return currentMethods.containsKey(AccessorKey.ofMethodWithSameSignatureButNewName(methodName, jApiMethod));
    }

    private static boolean isCurrentGetterThatReplacesBooleanIsGetter(JApiMethod jApiMethod, Map<AccessorKey, UpgradedProperty> currentMethods) {
        if (!isCurrentGetterOfUpgradedProperty(jApiMethod, currentMethods)) {
            return false;
        }

        UpgradedProperty method = currentMethods.get(AccessorKey.ofNewMethod(jApiMethod));
        if (method != null) {
            String propertyName = method.getPropertyName();
            String isGetterName = "is" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
            return method.getReplacedAccessors().stream()
                .anyMatch(replacedAccessor -> replacedAccessor.getName().equals(isGetterName) && replacedAccessor.getDescriptor().equals("()Z"));
        }
        return false;
    }

    private static boolean isOldMethod(JApiMethod jApiMethod, Map<AccessorKey, ReplacedAccessor> upgradedMethods, Pattern pattern) {
        String name = jApiMethod.getName();
        if (!pattern.matcher(name).matches()) {
            return false;
        }
        return upgradedMethods.containsKey(AccessorKey.ofOldMethod(jApiMethod));
    }

    private static boolean isCurrentGetterOfUpgradedProperty(JApiMethod jApiMethod, Map<AccessorKey, UpgradedProperty> currentMethods) {
        return isCurrentMethod(jApiMethod, currentMethods, GETTER_REGEX);
    }

    private static boolean isCurrentMethod(JApiMethod jApiMethod, Map<AccessorKey, UpgradedProperty> currentMethods, Pattern pattern) {
        String name = jApiMethod.getName();
        if (!pattern.matcher(name).matches()) {
            return false;
        }
        return currentMethods.containsKey(AccessorKey.ofNewMethod(jApiMethod));
    }

    public static Optional<AccessorKey> maybeGetKeyOfOldAccessorOfUpgradedProperty(JApiCompatibility jApiCompatibility, ViolationCheckContext context) {
        if (!(jApiCompatibility instanceof JApiMethod) || !((JApiMethod) jApiCompatibility).getOldMethod().isPresent()) {
            return Optional.empty();
        }
        JApiMethod jApiMethod = (JApiMethod) jApiCompatibility;
        Map<AccessorKey, ReplacedAccessor> oldMethods = context.getUserData(OLD_REMOVED_ACCESSORS_OF_UPGRADED_PROPERTIES);
        AccessorKey key = AccessorKey.ofOldMethod(jApiMethod);
        return oldMethods.containsKey(key) ? Optional.of(key) : Optional.empty();
    }
}
