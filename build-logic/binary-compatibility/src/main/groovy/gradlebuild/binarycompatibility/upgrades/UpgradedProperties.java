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

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import gradlebuild.binarycompatibility.upgrades.UpgradedProperty.AccessorKey;
import gradlebuild.binarycompatibility.upgrades.UpgradedProperty.ReplacedAccessor;
import japicmp.model.JApiCompatibility;
import japicmp.model.JApiMethod;
import me.champeau.gradle.japicmp.report.Violation;
import me.champeau.gradle.japicmp.report.ViolationCheckContext;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import static gradlebuild.binarycompatibility.rules.SinceAnnotationMissingRule.SINCE_ERROR_MESSAGE;
import static japicmp.model.JApiCompatibilityChange.METHOD_ADDED_TO_PUBLIC_CLASS;
import static japicmp.model.JApiCompatibilityChange.METHOD_REMOVED;
import static japicmp.model.JApiCompatibilityChange.METHOD_RETURN_TYPE_CHANGED;

public class UpgradedProperties {

    private static final Pattern SETTER_REGEX = Pattern.compile("set[A-Z].*");
    private static final Pattern GETTER_REGEX = Pattern.compile("get[A-Z].*");
    private static final Pattern BOOLEAN_GETTER_REGEX = Pattern.compile("is[A-Z].*");
    public static final String OLD_ACCESSORS_OF_UPGRADED_PROPERTIES = "oldAccessorsOfUpgradedProperties";
    public static final String SEEN_OLD_ACCESSORS_OF_UPGRADED_PROPERTIES = "seenOldAccessorsOfUpgradedProperties";
    public static final String CURRENT_ACCESSORS_OF_UPGRADED_PROPERTIES = "currentAccessorsOfUpgradedProperties";

    public static List<UpgradedProperty> parse(String path) {
        File file = new File(path);
        if (!file.exists()) {
            return Collections.emptyList();
        }
        try {
            return new Gson().fromJson(new FileReader(file), new TypeToken<List<UpgradedProperty>>() {}.getType());
        } catch (FileNotFoundException e) {
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
        if (violation.getHumanExplanation().startsWith(SINCE_ERROR_MESSAGE)) {
            // We still want to report the violation if @since is not added to a method
            return false;
        }

        Map<AccessorKey, UpgradedProperty> currentAccessors = context.getUserData(CURRENT_ACCESSORS_OF_UPGRADED_PROPERTIES);
        Map<AccessorKey, ReplacedAccessor> oldAccessors = context.getUserData(OLD_ACCESSORS_OF_UPGRADED_PROPERTIES);

        if (jApiMethod.getCompatibilityChanges().contains(METHOD_REMOVED)) {
            return isOldSetterOfUpgradedProperty(jApiMethod, oldAccessors) || isOldGetterOfUpgradedProperty(jApiMethod, oldAccessors) || isOldBooleanGetterOfUpgradedProperty(jApiMethod, oldAccessors);
        } else if (jApiMethod.getCompatibilityChanges().contains(METHOD_ADDED_TO_PUBLIC_CLASS)) {
            return isCurrentGetterOfUpgradedProperty(jApiMethod, currentAccessors);
        } else if (jApiMethod.getCompatibilityChanges().contains(METHOD_RETURN_TYPE_CHANGED)) {
            return isCurrentGetterOfUpgradedProperty(jApiMethod, currentAccessors) && isOldGetterOfUpgradedProperty(jApiMethod, oldAccessors);
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
        Map<AccessorKey, ReplacedAccessor> oldMethods = context.getUserData(OLD_ACCESSORS_OF_UPGRADED_PROPERTIES);
        AccessorKey key = AccessorKey.ofOldMethod(jApiMethod);
        return oldMethods.containsKey(key) ? Optional.of(key) : Optional.empty();
    }
}
