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
package org.gradle.launcher.daemon;

import org.gradle.api.GradleException;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author: Szczepan Faber, created at: 8/29/11
 */
public class DaemonTimeout {
    private static final String TIMEOUT_PROPERTY = "org.gradle.daemon.idletimeout";
    private final int idleTimeout;

    /**
     * parses input vm params and looks for the timeout property. If not found the default is used.
     */
    public DaemonTimeout(String vmParams, int defaultIdleTimeout) {
        String p = readProperty(vmParams);
        if (p != null) {
            idleTimeout = Integer.parseInt(p);
        } else {
            idleTimeout = defaultIdleTimeout;
        }
    }

    /**
     * throws exception if timeout property is not in the properties or when it is not a valid int
     */
    public DaemonTimeout(Map<String, String> sysProperties) {
        String timeoutProperty = sysProperties.get(TIMEOUT_PROPERTY);
        if (timeoutProperty == null) {
            throw new GradleException("System property: " + TIMEOUT_PROPERTY + " not found. It needs to be provided.");
        }
        try {
            idleTimeout = Integer.parseInt(timeoutProperty);
        } catch (Exception e) {
            throw new GradleException(String.format("Unable to parse %s sys property. The value should be an int but is: %s", TIMEOUT_PROPERTY, timeoutProperty));
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

    public int getIdleTimeout() {
        return idleTimeout;
    }

    /**
     * returns sys arg in proper console format
     */
    public String toSysArg() {
        return "-D" + TIMEOUT_PROPERTY + "=" + idleTimeout;
    }
}
