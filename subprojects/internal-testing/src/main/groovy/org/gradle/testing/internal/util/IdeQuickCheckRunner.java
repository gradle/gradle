/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.testing.internal.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;

public class IdeQuickCheckRunner {
    public static void main(String[] args) {
        Process process = null;

        try {
            ProcessBuilder builder = new ProcessBuilder().command("./gradlew", "quickCheck", "--daemon");
            process = builder.start();
            final Process finalProcess = process;
            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
                public void run() {
                    finalProcess.destroy();
                }
            }));
            forwardAsync(process.getInputStream(), System.out);
            forwardAsync(process.getErrorStream(), System.err);
            process.waitFor();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
            process.destroy();
        }
    }
    
    private static void forwardAsync(final InputStream input, final OutputStream output) {
        new Thread(new Runnable() {
            public void run() {
                int bufferSize = 4096;
                byte[] buffer = new byte[bufferSize];

                int read = 0;
                try {
                    read = input.read(buffer);
                    while(read != -1) {
                        output.write(buffer, 0, read);
                        read = input.read(buffer);
                    }
                } catch (IOException e) {
                    e.printStackTrace(new PrintWriter(output));
                }
            }
        }).start();
    }

}