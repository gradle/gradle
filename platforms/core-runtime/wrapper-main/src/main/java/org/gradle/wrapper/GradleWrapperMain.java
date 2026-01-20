/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.wrapper;

import org.gradle.cli.CommandLineParser;
import org.gradle.cli.ParsedCommandLine;
import org.gradle.cli.SystemPropertiesCommandLineConverter;
import org.jspecify.annotations.NullMarked;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import static org.gradle.wrapper.Download.UNKNOWN_VERSION;

public class GradleWrapperMain {

    public static final String GRADLE_USER_HOME_OPTION = "g";
    public static final String GRADLE_USER_HOME_DETAILED_OPTION = "gradle-user-home";
    public static final String GRADLE_QUIET_OPTION = "q";
    public static final String GRADLE_QUIET_DETAILED_OPTION = "quiet";

    public static void main(String[] args) throws Exception {
        File wrapperJar = wrapperJar();
        prepareWrapper(args, wrapperJar).execute();
    }

    private static Action prepareWrapper(String[] args, File wrapperJar) throws Exception {
        File propertiesFile = wrapperProperties(wrapperJar);
        File rootDir = rootDir(wrapperJar);

        CommandLineParser parser = new CommandLineParser();
        parser.allowUnknownOptions();
        parser.option(GRADLE_USER_HOME_OPTION, GRADLE_USER_HOME_DETAILED_OPTION).hasArgument();
        parser.option(GRADLE_QUIET_OPTION, GRADLE_QUIET_DETAILED_OPTION);

        SystemPropertiesCommandLineConverter converter = new SystemPropertiesCommandLineConverter();
        converter.configure(parser);
        ParsedCommandLine options = parser.parse(args);

        Map<String, String> commandLineSystemProperties = converter.convert(options, new HashMap<String, String>());
        Map<String, String> projectSystemProperties = PropertiesFileHandler.getSystemProperties(new File(rootDir, "gradle.properties"));
        /// If the Gradle system properties may define a custom Gradle home, which needs to be set before loading user gradle.properties
        maybeAddGradleUserHomeSystemProperty(projectSystemProperties, commandLineSystemProperties);
        File gradleUserHome = gradleUserHome(options);
        File userGradleProperties = new File(gradleUserHome, "gradle.properties");
        Map<String, String> userSystemProperties = new HashMap<>(PropertiesFileHandler.getSystemProperties(userGradleProperties));
        // Inception: Gradle user home cannot be changed with configuration from the Gradle user home
        boolean invalidGradleUserHome = userSystemProperties.remove(GradleUserHomeLookup.GRADLE_USER_HOME_PROPERTY_KEY) != null;
        // Set all system properties from all Gradle sources with correct precedence: project (lowest) < user < cli (highest)
        addSystemProperties(projectSystemProperties, userSystemProperties, commandLineSystemProperties);

        Logger logger = logger(options);

        if (invalidGradleUserHome) {
            logger.log("WARNING Ignored custom Gradle user home location configured in Gradle user home: " + userGradleProperties.getAbsolutePath());
        }

        WrapperExecutor wrapperExecutor = WrapperExecutor.forWrapperPropertiesFile(propertiesFile);
        WrapperConfiguration configuration = wrapperExecutor.getConfiguration();
        IDownload download = new Download(logger, "gradlew", UNKNOWN_VERSION, configuration.getNetworkTimeout());

        return () -> {
            wrapperExecutor.execute(
                args,
                new Install(logger, download, new PathAssembler(gradleUserHome, rootDir)),
                new BootstrapMainStarter());
        };
    }

    private static void addSystemProperties(Map<String, String> projectSystemProperties, Map<String, String> userSystemProperties, Map<String, String> commandLineSystemProperties) {
        Map<String, String> gradleSystemProperties = merge(merge(projectSystemProperties, userSystemProperties), commandLineSystemProperties);
        System.getProperties().putAll(gradleSystemProperties);
    }

    private static void maybeAddGradleUserHomeSystemProperty(Map<String, String> projectSystemProperties, Map<String, String> commandLineSystemProperties) {
        Map<String, String> gradleSystemProperties = merge(projectSystemProperties, commandLineSystemProperties);
        String property = gradleSystemProperties.get(GradleUserHomeLookup.GRADLE_USER_HOME_PROPERTY_KEY);
        if (property != null) {
            System.setProperty(GradleUserHomeLookup.GRADLE_USER_HOME_PROPERTY_KEY, property);
        }
    }

    private static Map<String, String> merge(Map<String, String> p1, Map<String, String> p2) {
        // If there are duplicate keys, the values from p2 take precedence.
        Map<String, String> result = new HashMap<>(p1);
        result.putAll(p2);
        return result;
    }

    private static File rootDir(File wrapperJar) {
        return wrapperJar.getParentFile().getParentFile().getParentFile();
    }

    private static File wrapperProperties(File wrapperJar) {
        return new File(wrapperJar.getParent(), wrapperJar.getName().replaceFirst("\\.jar$", ".properties"));
    }

    private static File wrapperJar() {
        URI location;
        try {
            location = GradleWrapperMain.class.getProtectionDomain().getCodeSource().getLocation().toURI();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        if (!location.getScheme().equals("file")) {
            throw new RuntimeException(String.format("Cannot determine classpath for wrapper Jar from codebase '%s'.", location));
        }
        try {
            return Paths.get(location).toFile();
        } catch (NoClassDefFoundError e) {
            return new File(location.getPath());
        }
    }

    private static File gradleUserHome(ParsedCommandLine options) {
        if (options.hasOption(GRADLE_USER_HOME_OPTION)) {
            return new File(options.option(GRADLE_USER_HOME_OPTION).getValue());
        }
        return GradleUserHomeLookup.gradleUserHome();
    }

    private static Logger logger(ParsedCommandLine options) {
        return new Logger(options.hasOption(GRADLE_QUIET_OPTION));
    }

    @NullMarked
    @FunctionalInterface
    private interface Action {
        void execute() throws Exception;
    }
}
