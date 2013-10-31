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

package org.gradle.nativebinaries.language.cpp.fixtures.app;

import java.util.Collections;
import java.util.List;

public abstract class HelloWorldApp extends TestApp {
    public static final String HELLO_WORLD = "Hello, World!";
    public static final String HELLO_WORLD_FRENCH = "Bonjour, Monde!";

    public String getEnglishOutput() {
        return HELLO_WORLD + "\n12";
    }

    public String getFrenchOutput() {
        return HELLO_WORLD_FRENCH + "\n12";
    }

    public String getExtraConfiguration() {
        return "";
    }

    public String getSourceType() {
        return getMainSource().getPath();
    }

    public List<String> getPluginList() {
        return Collections.singletonList(getSourceType());
    }

    public String getPluginScript() {
        StringBuilder builder = new StringBuilder();
        for (String plugin : getPluginList()) {
            builder.append("apply plugin: '").append(plugin).append("'\n");
        }
        return builder.toString();
    }

    public String compilerArgs(String arg) {
        return compilerConfig("args", arg);
    }

    public String compilerDefine(String define) {
        return compilerConfig("define", define);
    }

    private String compilerConfig(String action, String arg) {
        StringBuilder builder = new StringBuilder();
        for (String plugin : getPluginList()) {
            if (plugin.equals("c") || plugin.equals("cpp")) {
                builder.append(plugin).append("Compiler.").append(action).append(" '").append(arg).append("'\n");
            }
        }
        return builder.toString();
    }
}
