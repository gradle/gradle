import org.gradle.api.internal.GradleInternal

fun remoteBuildCacheEnabled() = (gradle as GradleInternal).settings.buildCache.remote?.isEnabled == true

fun getVendor() = System.getProperty("java.vendor")

fun getJavaHome() = System.getProperty("java.home")

if (remoteBuildCacheEnabled() && !getVendor().contains("Oracle") && !JavaVersion.current().isJava9()) {
    throw GradleException("Remote cache is enabled, which requires Oracle JDK 9 to perform this build. It's currently ${getVendor()} at ${getJavaHome()} ")
}

if (JavaVersion.current().isJava8() && System.getProperty("org.gradle.ignoreBuildJavaVersionCheck") == null) {
    throw GradleException("JDK 8 is required to perform this build. It's currently ${getVendor()} at ${getJavaHome()} ")
}
