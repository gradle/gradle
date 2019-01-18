package Gradle

import Gradle.vcsRoots.*
import jetbrains.buildServer.configs.kotlin.v2018_2.*
import jetbrains.buildServer.configs.kotlin.v2018_2.Project
import jetbrains.buildServer.configs.kotlin.v2018_2.projectFeatures.VersionedSettings
import jetbrains.buildServer.configs.kotlin.v2018_2.projectFeatures.versionedSettings

object Project : Project({
    uuid = "6316f07f-97fa-486b-8c14-6df0ff6e674b"
    id("Gradle")
    parentId("_Root")
    name = "Gradle"
    description = "Gradle build tool"

    vcsRoot(Gradle_Branches_GradlePersonalBranches)

    params {
        param("env.ORG_GRADLE_PROJECT_org.gradle.internal.plugins.portal.url.override", "http://dev12.gradle.org:8081/artifactory/gradle-plugins/")
        param("webhook.body", """
            {
            "text":" ${'$'}{buildResult} - *${'$'}{buildName}* <${'$'}{buildStatusUrl}|#${'$'}{buildNumber}> (triggered by ${'$'}{triggeredBy})"
            }
        """.trimIndent())
        param("env.REPO_MIRROR_URLS", "jcenter:http://dev12.gradle.org:8081/artifactory/jcenter,mavencentral:http://dev12.gradle.org:8081/artifactory/repo1,typesafemaven:http://dev12.gradle.org:8081/artifactory/typesafe-maven-releases,typesafeivy:http://dev12.gradle.org:8081/artifactory/typesafe-ivy-releases,google:http://dev12.gradle.org:8081/artifactory/google,lightbendmaven:http://dev12.gradle.org:8081/artifactory/typesafe-maven-releases,lightbendivy:http://dev12.gradle.org:8081/artifactory/typesafe-ivy-releases,springreleases:http://dev12.gradle.org:8081/artifactory/spring-releases/,springsnapshots:http://dev12.gradle.org:8081/artifactory/spring-snapshots/,restlet:http://dev12.gradle.org:8081/artifactory/restlet/,gradle-snapshots:http://dev12.gradle.org:8081/artifactory/gradle-snapshots/,gradle-releases:http://dev12.gradle.org:8081/artifactory/gradle-releases/,gradle:http://dev12.gradle.org:8081/artifactory/gradle-repo/,jboss:http://dev12.gradle.org:8081/artifactory/jboss/,gradleplugins:http://dev12.gradle.org:8081/artifactory/gradle-plugins/,gradlejavascript:http://dev12.gradle.org:8081/artifactory/gradle-javascript/,kotlindev:http://dev12.gradle.org:8081/artifactory/kotlin-dev/,kotlineap:http://dev12.gradle.org:8081/artifactory/kotlin-eap/,gradle-libs:http://dev12.gradle.org:8081/artifactory/gradle-libs/,groovy-snapshots:http://dev12.gradle.org:8081/artifactory/groovy-snapshots/,kotlinx:http://dev12.gradle.org:8081/artifactory/kotlinx/")
        param("maxParallelForks", "4")
        param("env.CI_REQUIRES_INVESTIGATION", "true")
        param("env.GRADLE_OPTS", "-XX:MaxPermSize=512m")
        password("env.SLACK_WEBHOOK_URL", "credentialsJSON:b53a855f-3d24-4333-9229-3374a3008c37", label = "Slack webhook URL", description = "URL for incoming Slack webhook used to send messages to public Slack channels")
        param("system.org.gradle.internal.plugins.portal.url.override", "http://dev12.gradle.org:8081/artifactory/gradle-plugins/")
        text("env.SLACK_PERFORMANCE_REPORT_CHANNEL", "#test-lptr", label = "Performance Slack channel", description = "Slack channel to report performance improvements to", allowEmpty = true)
    }

    features {
        versionedSettings {
            id = "PROJECT_EXT_19"
            mode = VersionedSettings.Mode.ENABLED
            buildSettingsMode = VersionedSettings.BuildSettingsMode.PREFER_SETTINGS_FROM_VCS
            rootExtId = "Gradle_Branches_VersionedSettings"
            showChanges = true
            settingsFormat = VersionedSettings.Format.KOTLIN
            storeSecureParamsOutsideOfVcs = true
        }
        feature {
            id = "PROJECT_EXT_64"
            type = "OAuthProvider"
            param("clientId", "5562c0dd4063cd7aac83")
            param("secure:clientSecret", "credentialsJSON:985b4ae9-d3ca-4b6e-a4bb-9fc020f9e26c")
            param("displayName", "GitHub")
            param("gitHubUrl", "https://github.com/")
            param("providerType", "GitHub")
        }
        feature {
            id = "PROJECT_EXT_65"
            type = "IssueTracker"
            param("secure:password", "")
            param("name", "gradle/gradle issues")
            param("pattern", """#(\d+)""")
            param("authType", "anonymous")
            param("repository", "https://github.com/gradle/gradle")
            param("type", "GithubIssues")
            param("secure:accessToken", "")
            param("username", "")
        }
        feature {
            id = "proj_customGraph1"
            type = "project-graphs"
            param("series", """
                [
                  {
                    "type": "valueType",
                    "title": "Build Duration (all stages)",
                    "sourceBuildTypeId": "Gradle_Master_Coverage_WindowsJava18_2",
                    "key": "BuildDuration"
                  },
                  {
                    "type": "valueType",
                    "title": "Build Duration (excluding Checkout Time)",
                    "sourceBuildTypeId": "Gradle_Master_Coverage_WindowsJava18_2",
                    "key": "BuildDurationNetTime"
                  }
                ]
            """.trimIndent())
            param("hideFilters", "")
            param("title", "Windows 1.8 Duration")
            param("defaultFilters", "")
            param("seriesTitle", "Serie")
        }
        feature {
            id = "project-graphs.order"
            type = "project-graphs.order"
            param("order", "proj_customGraph1")
        }
    }
})
