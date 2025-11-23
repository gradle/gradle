/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.plugins.ide.internal.tooling;

import org.gradle.api.Project;
import org.gradle.tooling.internal.build.DefaultHelp;
import org.gradle.tooling.internal.build.DefaultVersionInfo;
import org.gradle.tooling.internal.gradle.DefaultBuildIdentifier;
import org.gradle.tooling.model.build.Help;
import org.gradle.tooling.model.build.VersionInfo;
import org.gradle.tooling.provider.model.ToolingModelBuilder;

import java.lang.reflect.Method;

/**
 * Builds Help and VersionInfo models inside the build (for use from build actions).
 *
 * Implementation uses reflection to reuse the launcher-side CliTextPrinter when available
 * to keep parity with the command-line output, and falls back to minimal text otherwise.
 */
public class HelpModelBuilder implements ToolingModelBuilder {
    public HelpModelBuilder() { }

    @Override
    public boolean canBuild(String modelName) {
        return VersionInfo.class.getName().equals(modelName)
            || Help.class.getName().equals(modelName);
    }

    @Override
    public Object buildAll(String modelName, Project project) {
        if (VersionInfo.class.getName().equals(modelName)) {
            String banner = renderVersionInfoOrFallback();
            return new DefaultVersionInfo(new DefaultBuildIdentifier(project.getRootDir()), banner);
        }
        if (Help.class.getName().equals(modelName)) {
            String help = renderHelpOrFallback();
            return new DefaultHelp(help);
        }
        throw new IllegalArgumentException("Unsupported model: " + modelName);
    }


    private static String renderVersionInfoOrFallback() {
        try {
            Class<?> printer = Class.forName("org.gradle.launcher.cli.internal.CliTextPrinter");
            Class<?> metaType = Class.forName("org.gradle.initialization.BuildClientMetaData");
            Method m = printer.getMethod("renderVersionInfo", metaType, String.class);
            // We don't have daemon JVM criteria here; pass a descriptive fallback string.
            String daemonJvm = currentJvmDescription();
            Object meta = createBuildClientMetaData();
            Object result = m.invoke(null, meta, daemonJvm);
            return (String) result;
        } catch (Throwable ignored) {
            // Minimal banner fallback with just the Gradle version header
            String nl = System.lineSeparator();
            String version = gradleVersionOrUnknown();
            StringBuilder out = new StringBuilder();
            out.append(nl);
            out.append("------------------------------------------------------------").append(nl);
            out.append("Gradle ").append(version).append(nl);
            out.append("------------------------------------------------------------").append(nl);
            out.append(nl);
            return out.toString();
        }
    }

    private static String renderHelpOrFallback() {
        try {
            Class<?> printer = Class.forName("org.gradle.launcher.cli.internal.CliTextPrinter");
            Class<?> metaType = Class.forName("org.gradle.initialization.BuildClientMetaData");
            Method m = printer.getMethod("renderFullHelp", metaType, String.class);
            Object meta = createBuildClientMetaData();
            Object result = m.invoke(null, meta, null);
            return (String) result;
        } catch (Throwable ignored) {
            return "Help is not available.";
        }
    }

    private static Object createBuildClientMetaData() throws Exception {
        Class<?> launcherMeta = Class.forName("org.gradle.configuration.GradleLauncherMetaData");
        Object launcherMetaInstance = launcherMeta.getConstructor().newInstance();
        Class<?> defaultMeta = Class.forName("org.gradle.configuration.DefaultBuildClientMetaData");
        return defaultMeta.getConstructor(launcherMeta).newInstance(launcherMetaInstance);
    }

    private static String gradleVersionOrUnknown() {
        try {
            Class<?> dv = Class.forName("org.gradle.util.internal.DefaultGradleVersion");
            Method current = dv.getMethod("current");
            Object inst = current.invoke(null);
            Method getVersion = dv.getMethod("getVersion");
            return (String) getVersion.invoke(inst);
        } catch (Throwable ignored) {
            return "unknown";
        }
    }

    private static String currentJvmDescription() {
        String vendor = System.getProperty("java.vendor", "").trim();
        String version = System.getProperty("java.version", "").trim();
        String vm = System.getProperty("java.vm.name", "").trim();
        StringBuilder sb = new StringBuilder();
        if (!vm.isEmpty()) {
            sb.append(vm).append(" ");
        }
        if (!vendor.isEmpty()) {
            sb.append("(").append(vendor);
            if (!version.isEmpty()) {
                sb.append(" ").append(version);
            }
            sb.append(")");
        } else if (!version.isEmpty()) {
            sb.append(version);
        } else {
            sb.append("unknown");
        }
        return sb.toString();
    }
}



