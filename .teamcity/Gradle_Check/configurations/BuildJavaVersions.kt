package configurations

import common.JvmVendor
import common.JvmVersion
import common.Os

fun buildJavaHome(os: Os = Os.LINUX) = "%${os.name.toLowerCase()}.${JvmVersion.java11}.${JvmVendor.openjdk}.64bit%"

fun individualPerformanceTestJavaHome(os: Os) = "%${os.name.toLowerCase()}.${JvmVersion.java8}.${JvmVendor.oracle}.64bit%"
