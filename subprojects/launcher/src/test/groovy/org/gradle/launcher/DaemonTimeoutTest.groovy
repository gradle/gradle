package org.gradle.launcher

import spock.lang.Specification

/**
 * @author: Szczepan Faber, created at: 8/29/11
 */
class DaemonTimeoutTest extends Specification {

    def "knows timeout"() {
        expect:
        "-Dorg.gradle.daemon.idletimeout=1000" == new DaemonTimeout(null, 1000).toArg()
        "-Dorg.gradle.daemon.idletimeout=1000" == new DaemonTimeout("-Dfoo=bar", 1000).toArg()
        "-Dorg.gradle.daemon.idletimeout=1000" == new DaemonTimeout("-Dorg.gradle.daemon.idletimeout=foo", 1000).toArg()
        "-Dorg.gradle.daemon.idletimeout=2000" == new DaemonTimeout("-Dorg.gradle.daemon.idletimeout=2000", 1000).toArg()
    }
}
