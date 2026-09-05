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
import org.gradle.api.internal.plugins.ExecutableJar;
import org.gradle.api.internal.plugins.StartScriptGenerator;
import org.gradle.api.tasks.wrapper.Wrapper;
import org.gradle.api.tasks.wrapper.Wrapper.PathBase;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.util.PropertiesUtils;
import org.gradle.util.GradleVersion;
import org.gradle.util.internal.DefaultGradleVersion;
import org.gradle.util.internal.DistributionLocator;
import org.gradle.util.internal.GFileUtils;
import org.gradle.wrapper.WrapperExecutor;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.Locale;
import java.util.Properties;

@NullMarked
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

    public static String getWrapperJarUrl(GradleVersion gradleVersion) {
        return new DistributionLocator().getWrapperFor(gradleVersion).toASCIIString();
    }

    /**
     * Determines the wrapper JAR download URL.
     *
     * <p>If an explicit URL is configured it is used as-is. Otherwise the URL is derived from the
     * distribution URL (preserving a custom distribution host when it uses the standard
     * {@code -bin.zip}/{@code -all.zip} layout) or falls back to the official location for the given version.
     */
    public static String getWrapperJarUrl(GradleVersion gradleVersion, @Nullable String distributionUrl, @Nullable String explicitWrapperJarUrl) {
        if (explicitWrapperJarUrl != null && !explicitWrapperJarUrl.isEmpty()) {
            return explicitWrapperJarUrl;
        }
        if (distributionUrl != null) {
            int binIndex = distributionUrl.lastIndexOf("-bin.zip");
            if (binIndex >= 0) {
                return distributionUrl.substring(0, binIndex) + "-wrapper.jar";
            }
            int allIndex = distributionUrl.lastIndexOf("-all.zip");
            if (allIndex >= 0) {
                return distributionUrl.substring(0, allIndex) + "-wrapper.jar";
            }
        }
        return getWrapperJarUrl(gradleVersion);
    }

    public static void generate(
        PathBase archiveBase, String archivePath,
        PathBase distributionBase, String distributionPath,
        @Nullable String distributionSha256Sum,
        File wrapperPropertiesOutputFile,
        File wrapperJarOutputFile, String jarFileRelativePath,
        File unixScript, File batchScript,
        @Nullable String distributionUrl,
        @Nullable String wrapperJarUrl,
        boolean validateDistributionUrl,
        @Nullable Integer networkTimeout,
        @Nullable Integer retries,
        @Nullable Integer retryBackOffMs
    ) {
        writeProperties(wrapperPropertiesOutputFile, distributionUrl, wrapperJarUrl, distributionSha256Sum, distributionBase, distributionPath, archiveBase, archivePath, networkTimeout, validateDistributionUrl, retries, retryBackOffMs);
        writeWrapperJar(wrapperJarOutputFile);
        writeScripts(jarFileRelativePath, unixScript, batchScript);
    }

    private static void writeProperties(
        File propertiesFileDestination,
        @Nullable String distributionUrl,
        @Nullable String wrapperJarUrl,
        @Nullable String distributionSha256Sum,
        PathBase distributionBase,
        String distributionPath,
        PathBase archiveBase,
        String archivePath,
        @Nullable Integer networkTimeout,
        boolean validateDistributionUrl,
        @Nullable Integer retries,
        @Nullable Integer retryBackOffMs
    ) {
        Properties wrapperProperties = new Properties();
        wrapperProperties.put(WrapperExecutor.DISTRIBUTION_URL_PROPERTY, distributionUrl);
        if (wrapperJarUrl != null) {
            wrapperProperties.put(WrapperExecutor.WRAPPER_JAR_URL_PROPERTY, wrapperJarUrl);
        }
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
        if (retries != null) {
            wrapperProperties.put(WrapperExecutor.RETRIES_PROPERTY, String.valueOf(retries));
        }
        if (retryBackOffMs != null) {
            wrapperProperties.put(WrapperExecutor.RETRY_BACK_OFF_PROPERTY, String.valueOf(retryBackOffMs));
        }
        GFileUtils.parentMkdirs(propertiesFileDestination);
        try {
            PropertiesUtils.store(wrapperProperties, propertiesFileDestination);
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
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
        generator.setApplicationName("gradlew");
        generator.setGitRef(DefaultGradleVersion.current().getScriptTemplateGitRevision());
        generator.setEntryPoint(new ExecutableJar(jarFileRelativePath));
        generator.setClasspath(Collections.emptyList());
        generator.setOptsEnvironmentVar("GRADLE_OPTS");
        generator.setAppNameSystemProperty("org.gradle.appname");
        generator.setScriptRelPath(unixScript.getName());
        generator.setDefaultJvmOpts(ImmutableList.of("-Xmx64m", "-Xms64m"));

        generator.generateUnixScript(unixScript);
        generator.generateWindowsScript(batchScript);
        insertSafetyNet(batchScript);
        insertWrapperJarDownload(unixScript, batchScript, jarFileRelativePath);
    }

    private static final String UNIX_DOWNLOAD_ANCHOR = "# For Cygwin or MSYS, switch paths to Windows format before running java";
    private static final String WINDOWS_DOWNLOAD_ANCHOR = ":execute";

    private static void insertWrapperJarDownload(File unixScript, File batchScript, String jarFileRelativePath) {
        String unixJarRelative = jarFileRelativePath.replace("\\", "/");
        String unixPropsRelative = unixJarRelative.replaceAll("\\.jar$", ".properties");
        String unixJarPath = "$APP_HOME/" + unixJarRelative;
        String unixPropsPath = "$APP_HOME/" + unixPropsRelative;

        String windowsJarRelative = jarFileRelativePath.replace("/", "\\");
        String windowsPropsRelative = windowsJarRelative.replaceAll("\\.jar$", ".properties");
        String windowsJarPath = "%APP_HOME%\\" + windowsJarRelative;
        String windowsPropsPath = "%APP_HOME%\\" + windowsPropsRelative;

        insertUnixWrapperJarDownload(unixScript, unixJarPath, unixPropsPath);
        insertWindowsWrapperJarDownload(batchScript, windowsJarPath, windowsPropsPath);
    }

    private static void insertUnixWrapperJarDownload(File unixScript, String unixJarPath, String unixPropsPath) {
        String downloadBlock =
            "# Download the Gradle wrapper jar if it is missing (allows the jar to be omitted from version control).\n" +
            "# See https://github.com/gradle/gradle/issues/11816 for details.\n" +
            "WRAPPER_JAR=\"" + unixJarPath + "\"\n" +
            "WRAPPER_PROPERTIES=\"" + unixPropsPath + "\"\n" +
            "if [ ! -e \"$WRAPPER_JAR\" ]; then\n" +
            "    if [ ! -f \"$WRAPPER_PROPERTIES\" ]; then\n" +
            "        die \"Wrapper properties file not found: $WRAPPER_PROPERTIES\"\n" +
            "    fi\n" +
            "    WRAPPER_JAR_URL=\"$(sed -n 's/^wrapperJarUrl=//p' \"$WRAPPER_PROPERTIES\" | sed 's/\\\\:/:/g')\"\n" +
            "    if [ -z \"$WRAPPER_JAR_URL\" ]; then\n" +
            "        DISTRIBUTION_URL=\"$(sed -n 's/^distributionUrl=//p' \"$WRAPPER_PROPERTIES\" | sed 's/\\\\:/:/g')\"\n" +
            "        case \"$DISTRIBUTION_URL\" in\n" +
            "            http*-bin.zip|http*-all.zip)\n" +
            "                WRAPPER_JAR_URL=\"$(printf '%s' \"$DISTRIBUTION_URL\" | sed -e 's/-bin\\.zip$/-wrapper.jar/' -e 's/-all\\.zip$/-wrapper.jar/')\"\n" +
            "                ;;\n" +
            "            *)\n" +
            "                die \"Cannot determine wrapper jar download URL from distributionUrl. Set 'wrapperJarUrl' in $WRAPPER_PROPERTIES or commit the wrapper jar to version control.\"\n" +
            "                ;;\n" +
            "        esac\n" +
            "    fi\n" +
            "    echo \"Downloading Gradle wrapper jar from $WRAPPER_JAR_URL\" >&2\n" +
            "    mkdir -p \"${WRAPPER_JAR%/*}\"\n" +
            "    if command -v curl >/dev/null 2>&1; then\n" +
            "        if ! curl -fL -o \"$WRAPPER_JAR\" \"$WRAPPER_JAR_URL\"; then\n" +
            "            die \"Failed to download Gradle wrapper jar from $WRAPPER_JAR_URL\"\n" +
            "        fi\n" +
            "    elif command -v wget >/dev/null 2>&1; then\n" +
            "        if ! wget -O \"$WRAPPER_JAR\" \"$WRAPPER_JAR_URL\"; then\n" +
            "            die \"Failed to download Gradle wrapper jar from $WRAPPER_JAR_URL\"\n" +
            "        fi\n" +
            "    else\n" +
            "        die \"Gradle wrapper jar not found at $WRAPPER_JAR and neither curl nor wget is available to download it.\"\n" +
            "    fi\n" +
            "fi\n" +
            "\n";
        try {
            String script = new String(Files.readAllBytes(unixScript.toPath()), StandardCharsets.UTF_8);
            int anchorIndex = script.indexOf(UNIX_DOWNLOAD_ANCHOR);
            if (anchorIndex < 0 || script.indexOf(UNIX_DOWNLOAD_ANCHOR, anchorIndex + 1) >= 0) {
                throw new GradleException(
                    "Cannot insert wrapper jar download into " + unixScript
                        + ": expected exactly one occurrence of " + UNIX_DOWNLOAD_ANCHOR
                );
            }
            String updated = script.substring(0, anchorIndex) + downloadBlock + script.substring(anchorIndex);
            Files.write(unixScript.toPath(), updated.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to insert wrapper jar download into " + unixScript, e);
        }
    }

    private static void insertWindowsWrapperJarDownload(File batchScript, String windowsJarPath, String windowsPropsPath) {
        String downloadBlock =
            "@rem Download the Gradle wrapper jar if it is missing (allows the jar to be omitted from version control).\r\n" +
            "@rem See https://github.com/gradle/gradle/issues/11816 for details.\r\n" +
            "if exist \"" + windowsJarPath + "\" goto execute\r\n" +
            "if not exist \"" + windowsPropsPath + "\" (\r\n" +
            "    echo Wrapper properties file missing: \"" + windowsPropsPath + "\" 1>&2\r\n" +
            "    \"%COMSPEC%\" /c exit 1\r\n" +
            "    goto exitWithErrorLevel\r\n" +
            ")\r\n" +
            "set WRAPPER_JAR_URL=\r\n" +
            "for /f \"tokens=1,* delims==\" %%a in ('findstr \"wrapperJarUrl=\" \"" + windowsPropsPath + "\"') do set WRAPPER_JAR_URL=%%b\r\n" +
            "set WRAPPER_JAR_URL=%WRAPPER_JAR_URL:\\:=:%\r\n" +
            "if not \"%WRAPPER_JAR_URL%\"==\"\" goto downloadWrapperJar\r\n" +
            "set DISTRIBUTION_URL=\r\n" +
            "for /f \"tokens=1,* delims==\" %%a in ('findstr \"distributionUrl=\" \"" + windowsPropsPath + "\"') do set DISTRIBUTION_URL=%%b\r\n" +
            "set DISTRIBUTION_URL=%DISTRIBUTION_URL:\\:=:%\r\n" +
            "set WRAPPER_JAR_URL=%DISTRIBUTION_URL:-bin.zip=-wrapper.jar%\r\n" +
            "set WRAPPER_JAR_URL=%WRAPPER_JAR_URL:-all.zip=-wrapper.jar%\r\n" +
            ":downloadWrapperJar\r\n" +
            "echo.%WRAPPER_JAR_URL% | findstr /c:\"-wrapper.jar\" >NUL 2>&1\r\n" +
            "if %ERRORLEVEL% neq 0 (\r\n" +
            "    echo Cannot determine wrapper jar download URL. Set wrapperJarUrl in \"" + windowsPropsPath + "\" or commit the wrapper jar to version control. 1>&2\r\n" +
            "    \"%COMSPEC%\" /c exit 1\r\n" +
            "    goto exitWithErrorLevel\r\n" +
            ")\r\n" +
            "echo.%WRAPPER_JAR_URL% | findstr /b \"http\" >NUL 2>&1\r\n" +
            "if %ERRORLEVEL% neq 0 (\r\n" +
            "    echo Cannot determine wrapper jar download URL. Set wrapperJarUrl in \"" + windowsPropsPath + "\" or commit the wrapper jar to version control. 1>&2\r\n" +
            "    \"%COMSPEC%\" /c exit 1\r\n" +
            "    goto exitWithErrorLevel\r\n" +
            ")\r\n" +
            "echo Downloading Gradle wrapper jar from %WRAPPER_JAR_URL% 1>&2\r\n" +
            "where curl >NUL 2>&1\r\n" +
            "if %ERRORLEVEL% equ 0 goto downloadWrapperJarWithCurl\r\n" +
            "powershell -NoProfile -ExecutionPolicy Bypass -Command \"Invoke-WebRequest -Uri '%WRAPPER_JAR_URL%' -OutFile '" + windowsJarPath + "'\"\r\n" +
            "if %ERRORLEVEL% neq 0 (\r\n" +
            "    echo Failed to download Gradle wrapper jar from %WRAPPER_JAR_URL% 1>&2\r\n" +
            "    \"%COMSPEC%\" /c exit 1\r\n" +
            "    goto exitWithErrorLevel\r\n" +
            ")\r\n" +
            "goto downloadWrapperJarDone\r\n" +
            ":downloadWrapperJarWithCurl\r\n" +
            "curl -fL -o \"" + windowsJarPath + "\" \"%WRAPPER_JAR_URL%\"\r\n" +
            "if %ERRORLEVEL% neq 0 (\r\n" +
            "    echo Failed to download Gradle wrapper jar from %WRAPPER_JAR_URL% 1>&2\r\n" +
            "    \"%COMSPEC%\" /c exit 1\r\n" +
            "    goto exitWithErrorLevel\r\n" +
            ")\r\n" +
            ":downloadWrapperJarDone\r\n";
        try {
            String script = new String(Files.readAllBytes(batchScript.toPath()), StandardCharsets.ISO_8859_1);
            String anchor = WINDOWS_DOWNLOAD_ANCHOR + "\r\n";
            int anchorIndex = script.indexOf(anchor);
            if (anchorIndex < 0 || script.indexOf(anchor, anchorIndex + 1) >= 0) {
                // Fall back to LF-only check for robustness in tests
                anchor = WINDOWS_DOWNLOAD_ANCHOR + "\n";
                anchorIndex = script.indexOf(anchor);
                if (anchorIndex < 0 || script.indexOf(anchor, anchorIndex + 1) >= 0) {
                    throw new GradleException(
                        "Cannot insert wrapper jar download into " + batchScript
                            + ": expected exactly one occurrence of " + WINDOWS_DOWNLOAD_ANCHOR
                    );
                }
                String updated = script.substring(0, anchorIndex) + downloadBlock.replace("\r\n", "\n") + script.substring(anchorIndex);
                Files.write(batchScript.toPath(), updated.getBytes(StandardCharsets.ISO_8859_1));
                return;
            }
            String updated = script.substring(0, anchorIndex) + downloadBlock + script.substring(anchorIndex);
            Files.write(batchScript.toPath(), updated.getBytes(StandardCharsets.ISO_8859_1));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to insert wrapper jar download into " + batchScript, e);
        }
    }

    private static final String SAFETY_NET_ANCHOR = "setlocal EnableExtensions\r\n\r\n";
    private static final String SAFETY_NET =
        "@rem Catch executions from older scripts and ensure they exit cleanly.\r\n" +
            "@rem This can be removed once we can be reasonably confident that few people\r\n" +
            "@rem will be migrating directly to this new wrapper.\r\n" +
            "goto afterSafetyNet\r\n" +
            (":".repeat(78) + "\r\n").repeat(20) +
            "goto exitWithErrorLevel\r\n" +
            ":afterSafetyNet\r\n" +
            "\r\n";

    private static void insertSafetyNet(File batchScript) {
        try {
            String script = new String(Files.readAllBytes(batchScript.toPath()), StandardCharsets.ISO_8859_1);
            int anchorIndex = script.indexOf(SAFETY_NET_ANCHOR);
            if (anchorIndex < 0 || script.indexOf(SAFETY_NET_ANCHOR, anchorIndex + 1) >= 0) {
                throw new GradleException(
                    "Cannot insert the overwrite safety net into " + batchScript
                        + ": expected exactly one occurrence of " + SAFETY_NET_ANCHOR.trim()
                );
            }
            int insertionPoint = anchorIndex + SAFETY_NET_ANCHOR.length();
            String protectedScript = script.substring(0, insertionPoint) + SAFETY_NET + script.substring(insertionPoint);
            Files.write(batchScript.toPath(), protectedScript.getBytes(StandardCharsets.ISO_8859_1));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to insert the overwrite safety net into " + batchScript, e);
        }
    }

}
