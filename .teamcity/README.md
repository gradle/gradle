# CI Pipeline Configuration

- Currently there are three subprojects in the [Gradle](https://builds.gradle.org/project.html?projectId=Gradle) TeamCity project which are configured here
  - [Check](https://builds.gradle.org/project.html?projectId=Gradle_Check) - Main build pipeline
    (_Configured with [TeamCity's Kotlin DSL](https://confluence.jetbrains.com/display/TCD10/Kotlin+DSL)_)
  - [Promotion](https://builds.gradle.org/project.html?projectId=Gradle_Promotion) - Jobs to publish Gradle versions
    (_Configured directly in the TeamCity UI and stored in XML_)
  - [Util](https://builds.gradle.org/project.html?projectId=Gradle_Util) - Manually triggered utility jobs
    (_Configured directly in the TeamCity UI and stored in XML_)
- To configure/modify the _Check_ pipeline
  - The configurations are stored in the `.teamcity` folder and [tests](https://blog.jetbrains.com/teamcity/2017/02/kotlin-configuration-scripts-testing-configuration-scripts) in the `.teamcityTest` folder
  - Open the `.teamcity` folder in IDEA
  - Revert the changes made by IDEA to  `Gradle_Check_dsl.iml`
  - The main pipeline configuration can be found and modified in [CIBuildModel.kt](https://github.com/gradle/gradle/blob/master/.teamcity/Gradle_Check/model/CIBuildModel.kt)
  - After modifying, make sure that the configuration can be processed by running `CIConfigIntegrationTests`
  - Commit and push the changes
