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

package org.gradle.api.tasks.wrapper;

import org.gradle.wrapper.InstallMain;
import org.gradle.util.AntUtil;
import org.gradle.api.InvalidUserDataException;
import org.apache.tools.ant.taskdefs.Chmod;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.IOException;

/**
 * @author Hans Dockter
 */
public class WrapperScriptGenerator {
    public static final String UNIX_NL = "\n";
    public static final String WINDOWS_NL = "\n";
    public static final String CURRENT_DIR_UNIX = "`dirname \"$0\"`";
    public static final String CURRENT_DIR_WINDOWS = "%DIRNAME%";

    public void generate(String gradleVersion, String downloadUrlRoot, String jarPath, Wrapper.PathBase distributionBase,
                         String distributionPath, File scriptDestinationDir, Wrapper.PathBase zipStoreBase, String zipStorePath) {
        try {
            String windowsWrapperScriptHead = IOUtils.toString(Wrapper.class.getResourceAsStream("windowsWrapperScriptHead.txt"));
            String windowsWrapperScriptTail = IOUtils.toString(Wrapper.class.getResourceAsStream("windowsWrapperScriptTail.txt"));
            String unixWrapperScriptHead = IOUtils.toString(Wrapper.class.getResourceAsStream("unixWrapperScriptHead.txt"));
            String unixWrapperScriptTail = IOUtils.toString(Wrapper.class.getResourceAsStream("unixWrapperScriptTail.txt"));

//            String currentDirWindows = "\"%~dp0\\";
            String wrapperJarUnix = CURRENT_DIR_UNIX + "/" + FilenameUtils.separatorsToUnix(jarPath);
            String wrapperJarWindows = CURRENT_DIR_WINDOWS + "\\" + FilenameUtils.separatorsToWindows(jarPath);
            String distributionHomeUnix = getDistributionHomeUnix(distributionBase, distributionPath);
            String distributionHomeWindows = getDistributionHomeWindows(distributionBase, distributionPath);
            String gradleHomeUnix = distributionHomeUnix + "/gradle-" + gradleVersion;
            String gradleHomeWindows = distributionHomeWindows + "\\gradle-" + gradleVersion;
            String gradleWindowsPath = gradleHomeWindows + "\\bin";
            String fillingUnix = "" + UNIX_NL +
                    "STARTER_MAIN_CLASS=" + InstallMain.class.getName() + UNIX_NL +
                    "CLASSPATH=" + wrapperJarUnix + UNIX_NL +
                    "URL_ROOT=" + downloadUrlRoot + UNIX_NL +
                    "DIST_PATH=" + distributionHomeUnix + UNIX_NL +
                    "DIST_NAME=gradle-" + gradleVersion + "-bin" + UNIX_NL +
                    "ZIP_STORE=" + getDistributionHomeUnix(zipStoreBase, zipStorePath) + UNIX_NL +
                    "GRADLE_HOME=" + gradleHomeUnix + UNIX_NL +
                    "GRADLE=" + gradleHomeUnix + "/bin/gradle" + UNIX_NL;

            String fillingWindows = "" + WINDOWS_NL +
                    "set STARTER_MAIN_CLASS=" + InstallMain.class.getName() + WINDOWS_NL +
                    "set CLASSPATH=" + wrapperJarWindows + WINDOWS_NL +
                    "set URL_ROOT=" + downloadUrlRoot + WINDOWS_NL +
                    "set DIST_PATH=" + distributionHomeWindows + WINDOWS_NL +
                    "set DIST_NAME=gradle-" + gradleVersion + "-bin" + WINDOWS_NL +
                    "set ZIP_STORE=" + getDistributionHomeWindows(zipStoreBase, zipStorePath) + WINDOWS_NL +
                    "set GRADLE_HOME=" + gradleHomeWindows + WINDOWS_NL +
                    "set Path=" + gradleWindowsPath + ";%Path%" + WINDOWS_NL;

            String unixScript = unixWrapperScriptHead + fillingUnix + unixWrapperScriptTail;
            String windowsScript = windowsWrapperScriptHead + fillingWindows + windowsWrapperScriptTail;

            File unixScriptFile = new File(scriptDestinationDir, "gradlew");
            FileUtils.writeStringToFile(unixScriptFile, unixScript);

            Chmod chmod = new Chmod();
            chmod.setProject(AntUtil.createProject());
            chmod.setFile(unixScriptFile);
            chmod.setPerm("ugo+rx");
            chmod.execute();

            FileUtils.writeStringToFile(unixScriptFile, unixScript);
            FileUtils.writeStringToFile(new File(scriptDestinationDir, "gradlew.bat"), windowsScript);
        } catch (IOException e) {
            throw new InvalidUserDataException(e);
        }
    }

    private String getDistributionHomeWindows(Wrapper.PathBase pathBase, String distributionPath) {
        return (pathBase == Wrapper.PathBase.GRADLE_USER_HOME ? "%HOMEPATH%\\.gradle" : CURRENT_DIR_WINDOWS) +
                "\\" + distributionPath; 
    }

    private String getDistributionHomeUnix(Wrapper.PathBase pathBase, String distributionPath) {
        return (pathBase == Wrapper.PathBase.GRADLE_USER_HOME ? "~/.gradle" : CURRENT_DIR_UNIX) +
                "/" + distributionPath;
    }
}
