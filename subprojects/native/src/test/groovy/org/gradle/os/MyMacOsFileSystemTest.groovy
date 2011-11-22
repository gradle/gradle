package org.gradle.os

import spock.lang.IgnoreIf
import spock.lang.Specification

@IgnoreIf({ !OperatingSystem.current().macOsX })
class MyMacOsFileSystemTest extends Specification {
    def fs = new MyFileSystem()

    def "is case-sensitive"() {
        expect:
        fs.caseSensitive
    }

    def "is symlink-aware"() {
        expect:
        fs.symlinkAware
    }

    def "doesn't implicitly lock file on open"() {
        expect:
        !fs.implicitlyLocksFileOnOpen
    }
}
