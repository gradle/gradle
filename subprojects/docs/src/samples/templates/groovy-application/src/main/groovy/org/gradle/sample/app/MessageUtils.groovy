package org.gradle.sample.app

import groovy.transform.PackageScope

@PackageScope
class MessageUtils {
    static String getMessage() {
        return "Hello,      World!"
    }
}
