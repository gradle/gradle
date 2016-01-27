# Story: Detect STS Gradle installation and notify the user

Document the migration steps from STS Gradle integration to Buildship. Provide contribution to the STS plugin to show a dialog that the development is discontinued and users should use Buildship. The migration document should be accessible from the dialog.

## Estimate

- Migration guide - 1 day
- Detect STS installation - 3 days

## Implementation

- Write a migration guide for STS Gradle plugin users explaining how to transition to Buildship.
- Document features that are available in the STS Gradle plugin but not in Buildship.
It should reference the relevant points from the [STS plugin documentation](https://github.com/spring-projects/eclipse-integration-gradle/wiki).
    - Automatically run tasks on Gradle project import.
    - Allow Buildship setting files to be shared between developers.
        - Buildship stores absolute locations in the `.settings/gradle.prefs` import preference file. This makes the ‘Import existing project’ wizard unusable, and makes it difficult to share a fully configured Buildship workspace.
    - Import subset of modules from a multi-module build.
    - Support renaming for eclipse projects, or deduplicate naming conflicts with a non-Gradle project.
The document should be published as a wiki page in Buildship GitHub repository
- Contribute feature to STS plugin to render a message that users should migrate to Buildship
    - Contact The STS plugin developers in advance to express our intent and check if they will accept our contribution
    - Implement contribution
        - Upon plugin startup inform the user that the STS Gradle plugin is discontinued and that Pivotal will stop supporting it.
        - Add a link to the migration guide.
        - Add checkbox 'Don't show this message again'.

## Test cases

- Dialog is presented to the user upon startup
     - If the user does not check the option, then the dialog will continue to be rendered upon startup
- User can directly click on the link in the dialog and a browser will be opened

## Open issues

