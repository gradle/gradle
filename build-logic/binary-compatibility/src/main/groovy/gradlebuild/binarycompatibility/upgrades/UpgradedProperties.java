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

    private static final Pattern SETTER_REGEX = Pattern.compile("set[A-Z0-9].*");
    private static final Pattern GETTER_REGEX = Pattern.compile("get[A-Z0-9].*");
    private static final Pattern BOOLEAN_GETTER_REGEX = Pattern.compile("is[A-Z0-9].*");
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

    public static boolean shouldAcceptForUpgradedProperty(JApiMethod jApiMethod, Violation violation, ViolationCheckContext context) {
        if (violation.getHumanExplanation().startsWith(SINCE_ERROR_MESSAGE)) {
            // We still want to report the violation if @since is not added to a method
            return false;
        }

        Map<String, UpgradedProperty> currentMethods = context.getUserData(CURRENT_METHODS_OF_UPGRADED_PROPERTIES);
        Map<String, UpgradedProperty> oldMethods = context.getUserData(OLD_METHODS_OF_UPGRADED_PROPERTIES);

        if (jApiMethod.getCompatibilityChanges().contains(METHOD_REMOVED)) {
            return isOldSetterOfUpgradedProperty(jApiMethod, oldMethods) || isOldBooleanGetterOfUpgradedProperty(jApiMethod, oldMethods);
        } else if (jApiMethod.getCompatibilityChanges().contains(METHOD_RETURN_TYPE_CHANGED)) {
            return isCurrentGetterOfUpgradedProperty(jApiMethod, currentMethods) && isOldGetterOfUpgradedProperty(jApiMethod, oldMethods);
        } else if (jApiMethod.getCompatibilityChanges().contains(METHOD_ADDED_TO_PUBLIC_CLASS)) {
            return isCurrentGetterOfUpgradedProperty(jApiMethod, currentMethods);
        }

        return false;
    }

    private static boolean isOldSetterOfUpgradedProperty(JApiMethod jApiMethod, Map<String, UpgradedProperty> upgradedMethods) {
        return isOldMethod(jApiMethod, upgradedMethods, SETTER_REGEX);
    }

    private static boolean isOldGetterOfUpgradedProperty(JApiMethod jApiMethod, Map<String, UpgradedProperty> upgradedMethods) {
        return isOldMethod(jApiMethod, upgradedMethods, GETTER_REGEX);
    }

    private static boolean isOldBooleanGetterOfUpgradedProperty(JApiMethod jApiMethod, Map<String, UpgradedProperty> upgradedMethods) {
        return isOldMethod(jApiMethod, upgradedMethods, BOOLEAN_GETTER_REGEX) && jApiMethod.getReturnType().getOldReturnType().equals("boolean");
    }

    private static boolean isOldMethod(JApiMethod jApiMethod, Map<String, UpgradedProperty> upgradedMethods, Pattern pattern) {
        String name = jApiMethod.getName();
        if (!pattern.matcher(name).matches()) {
            return false;
        }
        String descriptor = jApiMethod.getOldMethod().get().getSignature();
        String containingType = jApiMethod.getjApiClass().getFullyQualifiedName();
        return upgradedMethods.containsKey(getMethodKey(containingType, name, descriptor));
    }

    private static boolean isCurrentGetterOfUpgradedProperty(JApiMethod jApiMethod, Map<String, UpgradedProperty> currentMethods) {
        return isCurrentMethod(jApiMethod, currentMethods, GETTER_REGEX);
    }

    private static boolean isCurrentMethod(JApiMethod jApiMethod, Map<String, UpgradedProperty> currentMethods, Pattern pattern) {
        String name = jApiMethod.getName();
        if (!pattern.matcher(name).matches()) {
            return false;
        }
        String descriptor = jApiMethod.getNewMethod().get().getSignature();
        String containingType = jApiMethod.getjApiClass().getFullyQualifiedName();
        return currentMethods.containsKey(getMethodKey(containingType, name, descriptor));
    }

    public static Optional<String> maybeGetKeyOfOldMethodOfUpgradedProperty(JApiCompatibility jApiCompatibility, ViolationCheckContext context) {
        if (!(jApiCompatibility instanceof JApiMethod) || !((JApiMethod) jApiCompatibility).getOldMethod().isPresent()) {
            return Optional.empty();
        }
        JApiMethod jApiMethod = (JApiMethod) jApiCompatibility;
        String name = jApiMethod.getName();
        Map<String, UpgradedProperty> oldMethods = context.getUserData(OLD_METHODS_OF_UPGRADED_PROPERTIES);
        String descriptor = jApiMethod.getOldMethod().get().getSignature();
        String containingType = jApiMethod.getjApiClass().getFullyQualifiedName();
        String key = getMethodKey(containingType, name, descriptor);
        return oldMethods.containsKey(key) ? Optional.of(key) : Optional.empty();
    }

    public static String getMethodKey(String containingType, String methodName, String descriptor) {
        return containingType + "#" + methodName + descriptor;
    }
}
