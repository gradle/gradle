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
import gradlebuild.binarycompatibility.upgrades.UpgradedProperty.UpgradedMethodKey;
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
    private static final String BOOLEAN_GETTER_DESCRIPTOR = "()Z";
    public static final String OLD_METHODS_OF_UPGRADED_PROPERTIES = "oldMethodsOfUpgradedProperties";
    public static final String SEEN_OLD_METHODS_OF_UPGRADED_PROPERTIES = "seenOldMethodsOfUpgradedProperties";
    public static final String CURRENT_METHODS_OF_UPGRADED_PROPERTIES = "currentMethodsOfUpgradedProperties";

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

        Map<UpgradedMethodKey, UpgradedProperty> currentMethods = context.getUserData(CURRENT_METHODS_OF_UPGRADED_PROPERTIES);
        Map<UpgradedMethodKey, UpgradedProperty> oldMethods = context.getUserData(OLD_METHODS_OF_UPGRADED_PROPERTIES);

        if (jApiMethod.getCompatibilityChanges().contains(METHOD_REMOVED)) {
            return isOldSetterOfUpgradedProperty(jApiMethod, oldMethods) || isOldBooleanGetterOfUpgradedProperty(jApiMethod, oldMethods);
        } else if (jApiMethod.getCompatibilityChanges().contains(METHOD_ADDED_TO_PUBLIC_CLASS)) {
            return isCurrentGetterOfUpgradedProperty(jApiMethod, currentMethods) && hasOldGetterOfUpgradedPropertyBooleanReturnType(jApiMethod, oldMethods, currentMethods);
        } else if (jApiMethod.getCompatibilityChanges().contains(METHOD_RETURN_TYPE_CHANGED)) {
            return isCurrentGetterOfUpgradedProperty(jApiMethod, currentMethods) && isOldGetterOfUpgradedProperty(jApiMethod, oldMethods);
        }

        return false;
    }

    private static boolean isOldSetterOfUpgradedProperty(JApiMethod jApiMethod, Map<UpgradedMethodKey, UpgradedProperty> upgradedMethods) {
        return isOldMethod(jApiMethod, upgradedMethods, SETTER_REGEX);
    }

    private static boolean isOldGetterOfUpgradedProperty(JApiMethod jApiMethod, Map<UpgradedMethodKey, UpgradedProperty> upgradedMethods) {
        return isOldMethod(jApiMethod, upgradedMethods, GETTER_REGEX);
    }

    private static boolean isOldBooleanGetterOfUpgradedProperty(JApiMethod jApiMethod, Map<UpgradedMethodKey, UpgradedProperty> upgradedMethods) {
        return isOldMethod(jApiMethod, upgradedMethods, BOOLEAN_GETTER_REGEX) && jApiMethod.getReturnType().getOldReturnType().equals("boolean");
    }

    private static boolean isOldMethod(JApiMethod jApiMethod, Map<UpgradedMethodKey, UpgradedProperty> upgradedMethods, Pattern pattern) {
        String name = jApiMethod.getName();
        if (!pattern.matcher(name).matches()) {
            return false;
        }
        return upgradedMethods.containsKey(UpgradedMethodKey.ofOldMethod(jApiMethod));
    }

    private static boolean isCurrentGetterOfUpgradedProperty(JApiMethod jApiMethod, Map<UpgradedMethodKey, UpgradedProperty> currentMethods) {
        return isCurrentMethod(jApiMethod, currentMethods, GETTER_REGEX);
    }

    private static boolean isCurrentMethod(JApiMethod jApiMethod, Map<UpgradedMethodKey, UpgradedProperty> currentMethods, Pattern pattern) {
        String name = jApiMethod.getName();
        if (!pattern.matcher(name).matches()) {
            return false;
        }
        return currentMethods.containsKey(UpgradedMethodKey.ofNewMethod(jApiMethod));
    }

    private static boolean hasOldGetterOfUpgradedPropertyBooleanReturnType(JApiMethod jApiMethod, Map<UpgradedMethodKey, UpgradedProperty> oldMethods, Map<UpgradedMethodKey, UpgradedProperty> currentMethods) {
        UpgradedProperty property = currentMethods.get(UpgradedMethodKey.ofNewMethod(jApiMethod));
        if (property != null) {
            String oldName = "is" + property.getPropertyName().substring(0, 1).toUpperCase() + property.getPropertyName().substring(1);
            return oldMethods.containsKey(UpgradedMethodKey.of(property.getContainingType(), oldName, BOOLEAN_GETTER_DESCRIPTOR));
        }
        return false;
    }

    public static Optional<UpgradedMethodKey> maybeGetKeyOfOldMethodOfUpgradedProperty(JApiCompatibility jApiCompatibility, ViolationCheckContext context) {
        if (!(jApiCompatibility instanceof JApiMethod) || !((JApiMethod) jApiCompatibility).getOldMethod().isPresent()) {
            return Optional.empty();
        }
        JApiMethod jApiMethod = (JApiMethod) jApiCompatibility;
        Map<UpgradedMethodKey, UpgradedProperty> oldMethods = context.getUserData(OLD_METHODS_OF_UPGRADED_PROPERTIES);
        UpgradedMethodKey key = UpgradedMethodKey.ofOldMethod(jApiMethod);
        return oldMethods.containsKey(key) ? Optional.of(key) : Optional.empty();
    }
}
