package org.gradle.build

import org.gradle.api.JavaVersion
import org.gradle.jvm.toolchain.internal.LocalJavaInstallation

class DefaultJavaInstallation implements LocalJavaInstallation {
    JavaVersion javaVersion
    File javaHome
    String displayName
    String name
}
