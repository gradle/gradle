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

package org.gradle.ide.xcode.internal;

import org.gradle.api.Project;
import org.gradle.util.internal.GUtil;

import java.util.Arrays;
import java.util.List;

public class XcodePropertyAdapter {
    private final Project project;

    public XcodePropertyAdapter(Project project) {
        this.project = project;
    }

    public String getAction() {
        return getXcodeProperty("ACTION");
    }

    public String getProductName() {
        return getXcodeProperty("PRODUCT_NAME");
    }

    public String getConfiguration() {
        return getXcodeProperty("CONFIGURATION");
    }

    public String getBuiltProductsDir() {
        return getXcodeProperty("BUILT_PRODUCTS_DIR");
    }

    private String getXcodeProperty(String name) {
        return String.valueOf(GUtil.elvis(project.findProperty(prefixName(name)), ""));
    }

    public static List<String> getAdapterCommandLine() {
        return Arrays.asList(
            toGradleProperty("ACTION"),
            toGradleProperty("PRODUCT_NAME"),
            toGradleProperty("CONFIGURATION"),
            toGradleProperty("BUILT_PRODUCTS_DIR")
        );
    }

    private static String toGradleProperty(String source) {
        return "-P" + prefixName(source) + "=\"${" + source + "}\"";
    }

    private static String prefixName(String source) {
        return "org.gradle.internal.xcode.bridge." + source;
    }
}
