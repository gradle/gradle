tasks.named<UpdateDaemonJvm>("updateDaemonJvm") {
    toolchainDownloadUrls = mapOf(
        BuildPlatformFactory.of(org.gradle.platform.Architecture.AARCH64, org.gradle.platform.OperatingSystem.MAC_OS) to uri("https://server?platform=MAC_OS.AARCH64"),
        BuildPlatformFactory.of(org.gradle.platform.Architecture.AARCH64, org.gradle.platform.OperatingSystem.WINDOWS) to uri("https://server?platform=WINDOWS.AARCH64")
    )
}
