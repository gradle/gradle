package org.gradle.client.logic.util

enum class DesktopOS(val id: String) {
    Linux("linux"),
    Mac("macos"),
    Windows("windows");
}

val currentDesktopOS: DesktopOS by lazy {
    val os = System.getProperty("os.name")
    when {
        os.startsWith("Linux", ignoreCase = true) -> DesktopOS.Linux
        os.equals("Mac OS X", ignoreCase = true) -> DesktopOS.Mac
        os.startsWith("Win", ignoreCase = true) -> DesktopOS.Windows
        else -> error("Unknown OS name: $os")
    }
}
