package org.gradle.os

import spock.lang.IgnoreIf
import spock.lang.Specification

@IgnoreIf({ !OperatingSystem.current().linux })
class MyLinuxFileSystemTest extends Specification {
    def fs = MyFileSystem.current()

    def "is case sensitive"() {
        expect:
        fs.caseSensitive
    }

    def "is symlink aware"() {
        expect:
        fs.symlinkAware
    }

    def "doesn't implicitly lock file on open"() {
        expect:
        !fs.implicitlyLocksFileOnOpen
    }
}