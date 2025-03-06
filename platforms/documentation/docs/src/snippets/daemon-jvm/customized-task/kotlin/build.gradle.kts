// tag::customized-daemon-jvm-task[]
tasks.named<UpdateDaemonJvm>("updateDaemonJvm") {
    languageVersion = JavaLanguageVersion.of(17)
}
// end::customized-daemon-jvm-task[]

// tag::customized-plat-daemon-jvm-task[]
tasks.named<UpdateDaemonJvm>("updateDaemonJvm") {
    val myPlatforms = mutableListOf(
        BuildPlatformFactory.of(
            org.gradle.platform.Architecture.AARCH64,
            org.gradle.platform.OperatingSystem.MAC_OS
        )
    )
    toolchainPlatforms.set(myPlatforms)
}
// end::customized-plat-daemon-jvm-task[]

// tag::customized-noplat-daemon-jvm-task[]
tasks.named<UpdateDaemonJvm>("updateDaemonJvm") {
    toolchainDownloadUrls.empty()
}
// end::customized-noplat-daemon-jvm-task[]

// tag::customized-uri-daemon-jvm-task[]
tasks.named<UpdateDaemonJvm>("updateDaemonJvm") {
    toolchainDownloadUrls = mapOf(
        BuildPlatformFactory.of(org.gradle.platform.Architecture.AARCH64, org.gradle.platform.OperatingSystem.MAC_OS) to uri("https://server?platform=MAC_OS.AARCH64"),
        BuildPlatformFactory.of(org.gradle.platform.Architecture.AARCH64, org.gradle.platform.OperatingSystem.WINDOWS) to uri("https://server?platform=WINDOWS.AARCH64")
    )
}
// end::customized-uri-daemon-jvm-task[]
