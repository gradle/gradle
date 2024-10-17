/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.process;

import java.io.File;
import java.net.URISyntaxException;

/**
 * An executable Java class that can be used standalone to verify javaexec routines.
 * It just runs its arguments as a command, so it can be used to bridge {@link JavaExecSpec} and {@link ShellScript}.
 */
public class TestJavaMain2 {
    // TODO(mlopatkin) Consider replacing TestJavaMain with this class + ShellScript
    public static String getClassLocation() {
        try {
            return new File(TestJavaMain.class.getProtectionDomain().getCodeSource().getLocation().toURI()).getAbsolutePath();
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void main(String[] args) throws Exception {
        Process p = new ProcessBuilder(args)
            .inheritIO()
            .directory(null)
            .start();
        System.exit(p.waitFor());
    }
}
