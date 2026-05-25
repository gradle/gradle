tasks.named<UpdateDaemonJvm>("updateDaemonJvm") {
    val myPlatforms = mutableListOf(
        BuildPlatformFactory.of(
            org.gradle.platform.Architecture.AARCH64,
            org.gradle.platform.OperatingSystem.MAC_OS
        )
    )
    toolchainPlatforms.set(myPlatforms)
}
