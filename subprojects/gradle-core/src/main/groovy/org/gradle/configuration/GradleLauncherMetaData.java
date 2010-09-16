/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.configuration;

import org.gradle.util.UncheckedException;

import java.io.IOException;

public class GradleLauncherMetaData {
    public void describeCommand(Appendable output, String... args) {
        try {
            String appName = System.getProperty("org.gradle.appname", "gradle");
            output.append(appName);
            for (String arg : args) {
                output.append(' ');
                output.append(arg);
            }
        } catch (IOException e) {
            throw UncheckedException.asUncheckedException(e);
        }
    }
}
