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
package org.gradle.launcher;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author: Szczepan Faber, created at: 8/29/11
 */
public class DaemonTimeout {
    public static final String TIMEOUT_PROPERTY = "org.gradle.daemon.idletimeout";
    private int idleTimeout;

    public DaemonTimeout(String vmParams, int defaultIdleTimeout) {
        String p = readProperty(vmParams);
        if (p != null) {
            idleTimeout = Integer.parseInt(p);
        } else {
            idleTimeout = defaultIdleTimeout;
        }
    }

    private String readProperty(String vmParams) {
        String idleDaemonTimeoutArg = null;
        if (vmParams != null) {
            Matcher m = Pattern.compile(".*-Dorg.gradle.daemon.idletimeout=(\\d+).*").matcher(vmParams);
            if (m.matches()) {
                idleDaemonTimeoutArg = m.group(1);
            }
        }
        return idleDaemonTimeoutArg;
    }

    public String toArg() {
        return "-D" + TIMEOUT_PROPERTY + "=" + idleTimeout;
    }
}
