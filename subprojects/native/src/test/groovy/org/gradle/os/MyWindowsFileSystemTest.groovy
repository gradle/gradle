package org.gradle.os

import spock.lang.IgnoreIf
import spock.lang.Specification

@IgnoreIf({ !OperatingSystem.current().windows })
class MyWindowsFileSystemTest extends Specification {
    def fs = MyFileSystem.current()

    def "is case insensitive"() {
        expect:
        !fs.caseSensitive
    }

    def "is not symlink aware"() {
        expect:
        !fs.symlinkAware
    }

    def "implicitly locks file on open"() {
        expect:
        fs.implicitlyLocksFileOnOpen
    }
}