# CI Pipeline Configuration

## Open & import the project

In your IDEA, `File` - `Open`, select `.teamcity/pom.xml`, `import as project`, and you'll have a Maven project.

## Project structure

Mostly a standard Maven project structure. The entry point `settings.kts` defines the TeamCity project.

There are 3 subprojects in the TeamCity project hierarchy: `Check` for Gradle builds, `Promotion` for releasing Gradle versions, `Util` for miscellaneous utilities.

## Develop and verify

After you make a change, you can run `mvn clean teamcity-configs:generate` to generate and verify the generated TeamCity configuration XMLs.

You also need to run `mvn clean verify` with Java 8 before committing changes.

If you have ktlint errors, you can automatically fix them by running `mvn com.github.gantsign.maven:ktlint-maven-plugin:1.1.1:format`.

## How the configuration works

We use Kotlin portable DSL to store TeamCity configuration, which means you can easily create a new pipeline
based on a specific branch. Currently, we have two pipelines: `master` and `release`, but you can easily create
and test another isolated pipeline from any branch. 

We'll explain everything via an example. Let's say you make some changes on your branch `myTestBranch`
(we highly recommend to name this branch without prefix and hyphen (`-`) because it's used to generate build type ID) and want to
test these changes without affecting `master`/`release` pipeline. Here are the instructions.

- Click the left sidebar "VCS Roots" here and create a VCS root [here](https://builds.gradle.org/admin/editProject.html?projectId=Gradle&cameFromUrl=%2Fproject.html%3FprojectId%3DGradle%26tab%3DprojectOverview%26branch_Gradle_Master_Check%3Dmaster)
  - Suppose the VCS root you just create is `MyNewVcsRoot`. Set "default branch" to `myTestBranch` where your code exists.
- Click `Create subproject` button at the bottom of the "Subprojects" region of [this page](https://builds.gradle.org/admin/editProject.html?projectId=Gradle&tab=projectGeneralTab)
  - Select `Manually`.
  - Give it a name. The name will be displayed on TeamCity web UI. We highly recommend it be capitalized from the branch name, i.e. `MyTestBranch`.
  - The project ID will be auto-generated as `Gradle_MyTestBranch`. If not, you probably selected wrong parent, the "Parent project" should be `Gradle`.
- Now click on the new project you just created. The URL should be `https://builds.gradle.org/admin/editProject.html?projectId=Gradle_<MyTestBranch>`.
- Click `Versioned Settings` on the left sidebar.
  - Select `Synchronization enabled` - `use settings from VCS` - `MyNewVcsRoot`(the one you just created) - `Settings format: Kotlin`, then `Apply`.
- At the popup window, click `Import Settings from VCS`. Wait a few seconds. 
  - If the error says "Context Parameter 'Branch' missing", it's ok. Click into the error and add a context parameter `Branch` with value `myTestBranch`. Go back and it automatically reloads.
  - If there are any errors, read the error and fix your code.
  - IMPORTANT NOTE: if the first import fails, you have to select and apply `Synchronization disabled`, then repeat the step above.
    Otherwise, TeamCity complains "Can't find the previous revision, please commit current settings first".
  - If anything bad happens, feel free to delete the project you created and retry (you may need to apply `Synchronization disabled` before deleting it).  
    
If no errors, your new pipeline will be displayed on TeamCity web UI:

```
Gradle
|------ Master
|        |--------- ...
|
|------ Release
|        |--------- ...
|                
|------ MyTestBranch
        |---------- Check
        |           |--------- QuickFeedbackLinux
        |           |--------- QuickFeedback
        |           |--------- ...
        |           |--------- ReadyForRelease
        |
        |---------- Promotion
        |           |--------- Publish Nightly Snapshot
        |           |--------- Publish Branch Snapshot
        |           |--------- ...
        |
        |---------- Util
                    |--------- ...
```

