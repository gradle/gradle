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

import com.google.common.collect.ImmutableSet;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import japicmp.model.JApiMethod;
import me.champeau.gradle.japicmp.report.ViolationCheckContext;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static japicmp.model.JApiCompatibilityChange.METHOD_ADDED_TO_PUBLIC_CLASS;
import static japicmp.model.JApiCompatibilityChange.METHOD_REMOVED;
import static japicmp.model.JApiCompatibilityChange.METHOD_RETURN_TYPE_CHANGED;

public class UpgradedProperties {

    private static final Set<String> LAZY_ACCEPTED_PROPERTY_TYPES = ImmutableSet.of(
        "org.gradle.api.provider.Property",
        "org.gradle.api.provider.ListProperty",
        "org.gradle.api.provider.SetProperty",
        "org.gradle.api.provider.MapProperty",
        "org.gradle.api.file.ConfigurableFileCollection"
    );

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

    public static boolean isUpgradedProperty(JApiMethod jApiMethod, ViolationCheckContext context) {
        if (jApiMethod.getCompatibilityChanges().contains(METHOD_REMOVED)) {
            return isOldUpgradedPropertySetter(jApiMethod) || isOldUpgradedBooleanPropertyGetter(jApiMethod);
        } else if (jApiMethod.getCompatibilityChanges().contains(METHOD_RETURN_TYPE_CHANGED)) {
            return isUpgradedPropertyGetter(jApiMethod);
        } else if (jApiMethod.getCompatibilityChanges().contains(METHOD_ADDED_TO_PUBLIC_CLASS)) {
            return isUpgradedPropertyGetter(jApiMethod) && isUpgradedBooleanPropertyGetter(jApiMethod);
        }
        return false;
    }

    private static boolean isUpgradedBooleanPropertyGetter(JApiMethod jApiMethod) {
        return false;
    }

    private static boolean isOldUpgradedBooleanPropertyGetter(JApiMethod jApiMethod) {
        return false;
    }

    private static boolean isOldUpgradedPropertySetter(JApiMethod jApiMethod) {
        return false;
    }

    private static boolean isUpgradedPropertyGetter(JApiMethod jApiMethod) {
        return jApiMethod.getName().startsWith("get")
            && jApiMethod.getParameters().isEmpty()
            && LAZY_ACCEPTED_PROPERTY_TYPES.contains(jApiMethod.getReturnType().getNewReturnType());
    }
}
