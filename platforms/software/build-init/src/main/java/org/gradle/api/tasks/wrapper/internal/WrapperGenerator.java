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

package org.gradle.api.tasks.wrapper.internal;

import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import org.gradle.api.GradleException;
import org.gradle.api.NonNullApi;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.plugins.StartScriptGenerator;
import org.gradle.api.tasks.wrapper.Wrapper;
import org.gradle.api.tasks.wrapper.Wrapper.PathBase;
import org.gradle.internal.util.PropertiesUtils;
import org.gradle.util.GradleVersion;
import org.gradle.util.internal.DistributionLocator;
import org.gradle.util.internal.GFileUtils;
import org.gradle.wrapper.GradleWrapperMain;
import org.gradle.wrapper.WrapperExecutor;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Properties;

import static java.util.Collections.singletonList;

@NonNullApi
public class WrapperGenerator {

    public static File getPropertiesFile(File jarFileDestination) {
        return new File(jarFileDestination.getParentFile(), jarFileDestination.getName().replaceAll("\\.jar$", ".properties"));
    }

    public static File getBatchScript(File scriptFile) {
        return new File(scriptFile.getParentFile(), scriptFile.getName().replaceFirst("(\\.[^\\.]+)?$", ".bat"));
    }

    public static String getDistributionUrl(GradleVersion gradleVersion, Wrapper.DistributionType distributionType) {
        String distType = distributionType.name().toLowerCase(Locale.ENGLISH);
        return new DistributionLocator().getDistributionFor(gradleVersion, distType).toASCIIString();
    }

    public static void generate(
        PathBase archiveBase, String archivePath,
        PathBase distributionBase, String distributionPath,
        @Nullable String distributionSha256Sum,
        File wrapperPropertiesOutputFile,
        File wrapperJarOutputFile, String jarFileRelativePath,
        File unixScript, File batchScript,
        @Nullable String distributionUrl,
        boolean validateDistributionUrl,
        @Nullable Integer networkTimeout
    ) {
        writeProperties(wrapperPropertiesOutputFile, distributionUrl, distributionSha256Sum, distributionBase, distributionPath, archiveBase, archivePath, networkTimeout, validateDistributionUrl);
        writeWrapperJar(wrapperJarOutputFile);
        writeScripts(jarFileRelativePath, unixScript, batchScript);
    }

    private static void writeProperties(
        File propertiesFileDestination,
        @Nullable String distributionUrl,
        @Nullable String distributionSha256Sum,
        PathBase distributionBase,
        String distributionPath,
        PathBase archiveBase,
        String archivePath,
        @Nullable Integer networkTimeout,
        boolean validateDistributionUrl
    ) {
        Properties wrapperProperties = new Properties();
        wrapperProperties.put(WrapperExecutor.DISTRIBUTION_URL_PROPERTY, distributionUrl);
        if (distributionSha256Sum != null) {
            wrapperProperties.put(WrapperExecutor.DISTRIBUTION_SHA_256_SUM, distributionSha256Sum);
        }
        wrapperProperties.put(WrapperExecutor.DISTRIBUTION_BASE_PROPERTY, distributionBase.toString());
        wrapperProperties.put(WrapperExecutor.DISTRIBUTION_PATH_PROPERTY, distributionPath);
        wrapperProperties.put(WrapperExecutor.ZIP_STORE_BASE_PROPERTY, archiveBase.toString());
        wrapperProperties.put(WrapperExecutor.ZIP_STORE_PATH_PROPERTY, archivePath);
        if (networkTimeout != null) {
            wrapperProperties.put(WrapperExecutor.NETWORK_TIMEOUT_PROPERTY, String.valueOf(networkTimeout));
        }
        wrapperProperties.put(WrapperExecutor.VALIDATE_DISTRIBUTION_URL, String.valueOf(validateDistributionUrl));

        GFileUtils.parentMkdirs(propertiesFileDestination);
        try {
            PropertiesUtils.store(wrapperProperties, propertiesFileDestination);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void writeWrapperJar(File destination) {
        URL jarFileSource = Wrapper.class.getResource("/gradle-wrapper.jar");
        if (jarFileSource == null) {
            throw new GradleException("Cannot locate wrapper JAR resource.");
        }
        try (InputStream in = jarFileSource.openStream(); OutputStream out = Files.newOutputStream(destination.toPath())) {
            ByteStreams.copy(in, out);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write wrapper JAR to " + destination, e);
        }
    }

    private static void writeScripts(String jarFileRelativePath, File unixScript, File batchScript) {
        StartScriptGenerator generator = new StartScriptGenerator();
        generator.setApplicationName("Gradle");
        generator.setMainClassName(GradleWrapperMain.class.getName());
        generator.setClasspath(singletonList(jarFileRelativePath));
        generator.setOptsEnvironmentVar("GRADLE_OPTS");
        generator.setExitEnvironmentVar("GRADLE_EXIT_CONSOLE");
        generator.setAppNameSystemProperty("org.gradle.appname");
        generator.setScriptRelPath(unixScript.getName());
        generator.setDefaultJvmOpts(ImmutableList.of("-Xmx64m", "-Xms64m"));
        generator.generateUnixScript(unixScript);
        generator.generateWindowsScript(batchScript);
    }

}
