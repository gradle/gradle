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

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.tools.ant.taskdefs.Chmod;
import org.gradle.api.InvalidUserDataException;
import org.gradle.util.AntUtil;
import org.gradle.wrapper.WrapperMain;

import java.io.File;
import java.io.IOException;

/**
 * @author Hans Dockter
 */
public class WrapperScriptGenerator {
    public static final String UNIX_NL = "\n";
    public static final String CURRENT_DIR_UNIX = "`dirname \"$0\"`";
    public static final String WINDOWS_NL = "\n";
    public static final String CURRENT_DIR_WINDOWS = "%DIRNAME%";

    public void generate(String jarPath, File scriptDestinationDir) {
        try {
            createUnixScript(jarPath, scriptDestinationDir);
            createWindowsScript(jarPath, scriptDestinationDir);
        } catch (IOException e) {
            throw new InvalidUserDataException(e);
        }
    }

    private void createUnixScript(String jarPath, File scriptDestinationDir) throws IOException {
        String unixWrapperScriptHead = IOUtils.toString(Wrapper.class.getResourceAsStream("unixWrapperScriptHead.txt"));
        String unixWrapperScriptTail = IOUtils.toString(Wrapper.class.getResourceAsStream("unixWrapperScriptTail.txt"));

        String fillingUnix = "" + UNIX_NL +
                "STARTER_MAIN_CLASS=" + WrapperMain.class.getName() + UNIX_NL +
                "CLASSPATH=" + CURRENT_DIR_UNIX + "/" + jarPath + UNIX_NL;

        String unixScript = unixWrapperScriptHead + fillingUnix + unixWrapperScriptTail;
        File unixScriptFile = new File(scriptDestinationDir, "gradlew");
        FileUtils.writeStringToFile(unixScriptFile, unixScript);
        createExecutablePermission(unixScriptFile);
    }

    private void createExecutablePermission(File unixScriptFile) {
        Chmod chmod = new Chmod();
        chmod.setProject(AntUtil.createProject());
        chmod.setFile(unixScriptFile);
        chmod.setPerm("ugo+rx");
        chmod.execute();
    }

    private void createWindowsScript(String jarPath, File scriptDestinationDir) throws IOException {
        String windowsWrapperScriptHead = IOUtils.toString(Wrapper.class.getResourceAsStream("windowsWrapperScriptHead.txt"));
        String windowsWrapperScriptTail = IOUtils.toString(Wrapper.class.getResourceAsStream("windowsWrapperScriptTail.txt"));
        String fillingWindows = "" + WINDOWS_NL +
                "STARTER_MAIN_CLASS=" + WrapperMain.class.getName() + WINDOWS_NL +
                "CLASSPATH=" + CURRENT_DIR_WINDOWS + "/" + jarPath + WINDOWS_NL;
        String windowsScript = windowsWrapperScriptHead + fillingWindows + windowsWrapperScriptTail;
        File windowsScriptFile = new File(scriptDestinationDir, "gradlew.bat");
        FileUtils.writeStringToFile(windowsScriptFile, windowsScript);
    }
}
