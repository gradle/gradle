/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.plugins.quality;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Represents the PMD targetjdk property available for PMD < 5.0
 * */
public enum TargetJdk {
    VERSION_1_3(false),
    VERSION_1_4(false),
    VERSION_1_5(true),
    VERSION_1_6(true),
    VERSION_1_7(true),
    VERSION_JSP(false);

    private boolean hasMajorVersion;

    private TargetJdk(boolean hasMajorVersion) {
        this.hasMajorVersion = hasMajorVersion;
    }

    /**
     * Converts the given object into a {@code JavaVersion}.
     *
     * @param value An object whose toString() value is to be converted. May be null.
     * @return The version, or null if the provided value is null.
     * @throws IllegalArgumentException when the provided value cannot be converted.
     */
    public static TargetJdk toVersion(Object value) throws IllegalArgumentException {
        if (value == null) {
            return null;
        }
        if (value instanceof TargetJdk) {
            return (TargetJdk) value;
        }

        String name = value.toString();
        if(name.equalsIgnoreCase("jsp")){
            return VERSION_JSP;
        }

        if (name.matches("\\d")) {
            int index = Integer.parseInt(name) - 1;
            if (index > 1 && index <= values().length && values()[index-2].hasMajorVersion) {
                return values()[index-2];
            }
        }

        Matcher matcher = Pattern.compile("1\\.(\\d)(\\D.*)?").matcher(name);
        if (matcher.matches()) {
            int versionIdx = Integer.parseInt(matcher.group(1)) - 1;
            if (versionIdx > 1 && versionIdx <= values().length) {
                return values()[versionIdx - 2];
            }
        }
        throw new IllegalArgumentException(String.format("Could not determine java version from '%s'.", name));
    }

    @Override
    public String toString() {
        return getName();
    }

    private String getName() {
        return name().substring("VERSION_".length()).replace('_', '.').toLowerCase();
    }
}
