/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.util.internal;

import org.apache.tools.ant.taskdefs.condition.Os;
import org.apache.tools.ant.util.FileUtils;

import java.io.File;

/**
 * This implementation is copied over from ant's JavaEnvUtils. The implementation
 * contains only one change - we pass the javaHome string as parameter rather than
 * hardcode it to System.getProperty('java.home')
 * <p>
 * We could potentially improve the implementation and lose the coupling to ant completely.
 * However, at the moment I'm playing it safe and I keep the implementation as it was.
 *
 * by Szczepan Faber, created at: 1/16/12
 */
public class AntBasedJavaExecutableFinder implements JavaExecutableFinder {

    //TODO SF: try adding some tests.

    private String javaHome;

    public AntBasedJavaExecutableFinder(String javaHome) {
        this.javaHome = javaHome;
    }

    /** Are we on a DOS-based system */
    static boolean isDos = Os.isFamily("dos");

    /** Are we on Novell NetWare */
    static boolean isNetware = Os.isName("netware");

    /** Are we on AIX */
    static boolean isAix = Os.isName("aix");

    /**
     * Finds an executable that is part of a JRE installation based on
     * the javaHome file.
     *
     * <p><code>java</code>, <code>keytool</code>,
     * <code>policytool</code>, <code>orbd</code>, <code>rmid</code>,
     * <code>rmiregistry</code>, <code>servertool</code> and
     * <code>tnameserv</code> are JRE executables on Sun based
     * JRE's.</p>
     *
     * <p>You typically find them in <code>JAVA_HOME/jre/bin</code> if
     * <code>JAVA_HOME</code> points to your JDK installation.  JDK
     * &lt; 1.2 has them in the same directory as the JDK
     * executables.</p>
     *
     * @param command the java executable to find.
     * @return the path to the command.
     */
    String getJreExecutable(String command) {
        if (isNetware) {
            // Extrapolating from:
            // "NetWare may have a "java" in that directory, but 99% of
            // the time, you don't want to execute it" -- Jeff Tulley
            // <JTULLEY@novell.com>
            return command;
        }

        File jExecutable = null;

        if (isAix) {
            // On IBM's JDK 1.2 the directory layout is different, 1.3 follows
            // Sun's layout.
            jExecutable = findInDir(javaHome + "/sh", command);
        }

        if (jExecutable == null) {
            jExecutable = findInDir(javaHome + "/bin", command);
        }

        if (jExecutable != null) {
            return jExecutable.getAbsolutePath();
        } else {
            // Unfortunately on Windows java.home doesn't always refer
            // to the correct location, so we need to fall back to
            // assuming java is somewhere on the PATH.
            return addExtension(command);
        }
    }

    /**
     * {@inheritDoc}
     */
    public String getJdkExecutable(String command) {
        if (isNetware) {
            // Extrapolating from:
            // "NetWare may have a "java" in that directory, but 99% of
            // the time, you don't want to execute it" -- Jeff Tulley
            // <JTULLEY@novell.com>
            return command;
        }

        File executable = null;

        if (isAix) {
            // On IBM's JDK 1.2 the directory layout is different, 1.3 follows
            // Sun's layout.
            executable = findInDir(javaHome + "/../sh", command);
        }

        if (executable == null) {
            executable = findInDir(javaHome + "/../bin", command);
        }

        if (executable != null) {
            return executable.getAbsolutePath();
        } else {
            // fall back to JRE bin directory, also catches JDK 1.0 and 1.1
            // where java.home points to the root of the JDK and Mac OS X where
            // the whole directory layout is different from Sun's
            return getJreExecutable(command);
        }
    }

    /**
     * Adds a system specific extension to the name of an executable.
     */
    String addExtension(String command) {
        // This is the most common extension case - exe for windows and OS/2,
        // nothing for *nix.
        return command + (isDos ? ".exe" : "");
    }

    /**
     * Look for an executable in a given directory.
     *
     * @return null if the executable cannot be found.
     */
    File findInDir(String dirName, String commandName) {
        File dir = FileUtils.getFileUtils().normalize(dirName);
        File executable = null;
        if (dir.exists()) {
            executable = new File(dir, addExtension(commandName));
            if (!executable.exists()) {
                executable = null;
            }
        }
        return executable;
    }
}
