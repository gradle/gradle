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
