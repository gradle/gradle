/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.language.nativeplatform.internal;

import org.apache.commons.lang.StringUtils;

public abstract class Names {

    public static Names of(String name) {
        if (name.equals("main")) {
            return new Main();
        }
        return new Other(name, name);
    }

    public static Names of(String name, String baseName) {
        return new Other(name, baseName);
    }

    public abstract Names append(String suffix);

    /**
     * The raw name.
     */
    public abstract String getName();

    /**
     * Camel case formatted base name.
     */
    public abstract String getBaseName();

    /**
     * Lower case formatted base name, with '_' separators
     */
    public abstract String getLowerBaseName();

    public abstract String withPrefix(String prefix);

    public abstract String withSuffix(String suffix);

    public abstract String getTaskName(String action);

    public abstract String getCompileTaskName(String language);

    // Includes trailing '/'
    public abstract String getDirName();

    private static class Main extends Names {
        @Override
        public String getName() {
            return "main";
        }

        @Override
        public String getBaseName() {
            return "main";
        }

        @Override
        public String getLowerBaseName() {
            return "main";
        }

        @Override
        public String getCompileTaskName(String language) {
            return "compile" + StringUtils.capitalize(language);
        }

        @Override
        public String getTaskName(String action) {
            return action;
        }

        @Override
        public String getDirName() {
            return "main/";
        }

        @Override
        public String withPrefix(String prefix) {
            return prefix;
        }

        @Override
        public String withSuffix(String suffix) {
            return suffix;
        }

        @Override
        public Names append(String suffix) {
            return Names.of("main" + StringUtils.capitalize(suffix));
        }
    }

    private static class Other extends Names {
        private final String name;
        private final String baseName;
        private final String lowerBaseName;
        private final String capitalizedBaseName;
        private final String dirName;

        Other(String rawName, String name) {
            this.name = rawName;
            StringBuilder baseName = new StringBuilder();
            StringBuilder lowerBaseName = new StringBuilder();
            StringBuilder capBaseName = new StringBuilder();
            StringBuilder dirName = new StringBuilder();
            int startLast = 0;
            int i = 0;
            for (; i < name.length(); i++) {
                if (Character.isUpperCase(name.charAt(i))) {
                    if (i > startLast) {
                        append(name, startLast, i, baseName, lowerBaseName, capBaseName, dirName);
                    }
                    startLast = i;
                }
            }
            if (i > startLast) {
                append(name, startLast, i, baseName, lowerBaseName, capBaseName, dirName);
            }
            this.baseName = baseName.toString();
            this.lowerBaseName = lowerBaseName.toString();
            this.capitalizedBaseName = capBaseName.toString();
            this.dirName = dirName.toString();
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getBaseName() {
            return baseName;
        }

        @Override
        public String getLowerBaseName() {
            return lowerBaseName;
        }

        @Override
        public String withPrefix(String prefix) {
            return prefix + capitalizedBaseName;
        }

        @Override
        public String withSuffix(String suffix) {
            return baseName + StringUtils.capitalize(suffix);
        }

        @Override
        public String getTaskName(String action) {
            return action + capitalizedBaseName;
        }

        @Override
        public String getCompileTaskName(String language) {
            return "compile" + capitalizedBaseName + StringUtils.capitalize(language);
        }

        // Includes trailing '/'
        @Override
        public String getDirName() {
            return dirName;
        }

        @Override
        public Names append(String suffix) {
            return Names.of(name + StringUtils.capitalize(suffix));
        }

        private void append(String name, int start, int end, StringBuilder baseName, StringBuilder lowerBaseName, StringBuilder capBaseName, StringBuilder dirName) {
            dirName.append(Character.toLowerCase(name.charAt(start)));
            dirName.append(name.substring(start + 1, end));
            dirName.append('/');
            if (start != 0 || end != 4 || !name.startsWith("main")) {
                if (baseName.length() == 0) {
                    baseName.append(Character.toLowerCase(name.charAt(start)));
                    baseName.append(name.substring(start + 1, end));
                } else {
                    baseName.append(name.substring(start, end));
                    lowerBaseName.append('-');
                }
                lowerBaseName.append(Character.toLowerCase(name.charAt(start)));
                lowerBaseName.append(name.substring(start + 1, end));
                capBaseName.append(Character.toUpperCase(name.charAt(start)));
                capBaseName.append(name.substring(start + 1, end));
            }
        }
    }

}
