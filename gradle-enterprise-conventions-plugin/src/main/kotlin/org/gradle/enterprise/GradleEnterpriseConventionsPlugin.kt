package org.gradle.enterprise

import com.gradle.enterprise.gradleplugin.GradleEnterpriseExtension
import com.gradle.enterprise.gradleplugin.GradleEnterprisePlugin
import com.gradle.scan.plugin.BuildScanExtension
import com.gradle.scan.plugin.internal.api.BuildScanExtensionWithHiddenFeatures
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.caching.configuration.BuildCacheConfiguration
import org.gradle.caching.http.HttpBuildCache
import java.net.InetAddress
import java.net.URI
import java.util.stream.Collectors


val isCiServer = System.getenv().containsKey("CI")
val gradleEnterpriseServerUrl = System.getProperty("gradle.enterprise.url") ?: "https://ge.gradle.org"
val remoteCacheUrl = System.getProperty("gradle.cache.remote.url")?.let { URI(it) } ?: determineCacheNode()
val remotePush = System.getProperty("gradle.cache.remote.push") != "false"
val remoteCacheUsername = System.getProperty("gradle.cache.remote.username", "")
val remoteCachePassword = System.getProperty("gradle.cache.remote.password", "")
val disableLocalCache = System.getProperty("disableLocalCache")?.toBoolean() ?: false

fun determineCacheNode(): URI {
    return when (val cacheNode = System.getProperty("cacheNode", "eu")) {
        "eu" -> URI("https://eu-build-cache.gradle.org/cache/")
        "us" -> URI("https://us-build-cache.gradle.org/cache/")
        "au" -> URI("https://au-build-cache.gradle.org/cache/")
        else -> throw IllegalArgumentException("Unrecognized cacheNode: $cacheNode")
    }
}

class GradleEnterpriseConventionsPlugin : Plugin<Settings> {
    private val buildScanCustomValueProviders: List<BuildScanCustomValueProvider> = listOf(
        CITagProvider,
        TravisCICustomValueProvider,
        JenkinsCiCustomValueProvider,
        GitHubActionsCustomValueProvider,
        TeamCityCustomValueProvider,
        LocalBuildCustomValueProvider,
        GitInformationCustomValueProvider
    )

    override fun apply(settings: Settings) {
        settings.plugins.withType(GradleEnterprisePlugin::class.java) {
            if (settings.gradle.startParameter.isBuildCacheEnabled) {
                settings.buildCache(BuildCacheConfigureAction())
            }
            if (!settings.gradle.startParameter.isNoBuildScan) {
                BuildScanConfigureAction(buildScanCustomValueProviders).execute(settings.extensions.getByType(GradleEnterpriseExtension::class.java).buildScan)
            }
        }
    }
}

class BuildCacheConfigureAction : Action<BuildCacheConfiguration> {
    override fun execute(buildCache: BuildCacheConfiguration) {
        buildCache.remote(HttpBuildCache::class.java) { remoteBuildCache ->
            remoteBuildCache.url = remoteCacheUrl
            remoteBuildCache.isPush = isCiServer && remotePush
            if (remoteCacheUsername.isNotEmpty() && remoteCachePassword.isNotEmpty()) {
                remoteBuildCache.credentials {
                    it.username = remoteCacheUsername
                    it.password = remoteCachePassword
                }
            }
        }

        if (disableLocalCache) {
            buildCache.local { localBuildCache ->
                localBuildCache.isEnabled = false
            }
        }
    }
}

interface BuildScanCustomValueProvider : Action<BuildScanExtension> {
    fun enabled(): Boolean = true
}

class BuildScanConfigureAction(private val customValueProviders: List<BuildScanCustomValueProvider>) : Action<BuildScanExtension> {
    override fun execute(buildScan: BuildScanExtension) {
        configureBuildScan(buildScan)
        customValueProviders.filter { it.enabled() }.forEach { it.execute(buildScan) }
    }

    private fun configureBuildScan(buildScan: BuildScanExtension) {
        buildScan.apply {
            buildScan.server = gradleEnterpriseServerUrl
            buildScan.isCaptureTaskInputFiles = true
            buildScan.obfuscation.ipAddresses { addresses: List<InetAddress?> -> addresses.stream().map { "0.0.0.0" }.collect(Collectors.toList()) }
            buildScan.publishAlways()
            (buildScan as BuildScanExtensionWithHiddenFeatures).publishIfAuthenticated()
            try {
                buildScan.isUploadInBackground = !isCiServer
            } catch (ex: NoSuchMethodError) {
                // GE Plugin version < 3.3. Continue
            }
        }
    }
}
