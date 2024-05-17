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

package org.gradle.nativeplatform.fixtures.app;

import org.apache.commons.io.FilenameUtils;
import org.gradle.internal.InternalTransformer;
import org.gradle.util.internal.CollectionUtils;

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

    public String getCustomOutput(String value) {
        return value + "\n12";
    }

    public String getExtraConfiguration() {
        return "";
    }

    public String getExtraConfiguration(String binaryName) {
        return "";
    }

    public String getSourceType() {
        return getMainSource().getPath();
    }

    public String getSourceExtension() {
        return FilenameUtils.getExtension(getMainSource().getName());
    }

    public List<String> getPluginList() {
        return Collections.singletonList(getNormalizedPluginName());
    }

    private String getNormalizedPluginName() {
        return getSourceType().replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
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

    public String compilerDefine(String define, String value) {
        return compilerConfig("define", define, value);
    }

    private String compilerConfig(String action, String... args) {
        String quotedArgs = CollectionUtils.join(",", CollectionUtils.collect(args, new SingleQuotingTransformer()));
        StringBuilder builder = new StringBuilder();
        for (String plugin : getPluginList()) {
            String compilerPrefix = getCompilerPrefix(plugin);
            if (compilerPrefix == null) {
                continue;
            }
            builder.append(compilerPrefix).append("Compiler.").append(action).append(" ").append(quotedArgs).append("\n");
        }

        return builder.toString();
    }

    private String getCompilerPrefix(String plugin) {
        if (plugin.equals("c")) {
            return "c";
        }
        if (plugin.equals("cpp")) {
            return "cpp";
        }
        if (plugin.equals("objective-c")) {
            return "objc";
        }
        if (plugin.equals("objective-cpp")) {
            return "objcpp";
        }
        return null;
    }

    private static class SingleQuotingTransformer implements InternalTransformer<Object, String> {
        @Override
        public Object transform(String original) {
            return "'" + original + "'";
        }
    }
}
