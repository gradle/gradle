import com.gradle.enterprise.gradleplugin.GradleEnterprisePlugin
import com.gradle.scan.plugin.BuildScanPlugin
import org.gradle.util.GradleVersion
import org.gradle.api.Project

initscript {
    val pluginVersion = "3.3.4"

    repositories {
        gradlePluginPortal()
    }
    dependencies {
        classpath("com.gradle:gradle-enterprise-gradle-plugin:${pluginVersion}")
    }
}

val isTopLevelBuild = gradle.getParent() == null
if (isTopLevelBuild) {
    val gradleVersion = GradleVersion.current().baseVersion
    val atLeastGradle5 = gradleVersion >= GradleVersion.version("5.0")
    val atLeastGradle6 = gradleVersion >= GradleVersion.version("6.0")

    if (atLeastGradle6) {
         settingsEvaluated {
            if (!pluginManager.hasPlugin("com.gradle.enterprise")) {
                pluginManager.apply(GradleEnterprisePlugin::class)
            }
            configureExtension(extensions["gradleEnterprise"], rootProject.name)
        }
    } else if (atLeastGradle5) {
        rootProject {
            pluginManager.apply(BuildScanPlugin::class)
            configureExtension(extensions["gradleEnterprise"], name)
        }
    }
}

val projectToGradleEnterpriseServer = mapOf("gradle" to "https://ge.gradle.org")

fun configureExtension(extension: Any, projectName: String) {
    extension.withGroovyBuilder {
        setProperty("server", projectToGradleEnterpriseServer.getOrDefault(projectName, "https://e.grdev.net"))
        getProperty("buildScan").withGroovyBuilder {
            "publishAlways"()
        }
    }
}
