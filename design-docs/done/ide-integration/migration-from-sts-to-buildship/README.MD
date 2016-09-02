# Story: Detect STS Gradle installation and notify the user

Document the migration steps from STS Gradle integration to Buildship. Provide contribution to the STS plugin to show a dialog that the development is discontinued and users should use Buildship. The migration document should be accessible from the dialog.

## Estimate

- Migration guide - 1 day
- Detect STS installation - 3 days

## Implementation

**Create a migration guide**
- Write a migration guide for STS Gradle plugin users explaining how to transition to Buildship.
- Document features that are available in the STS Gradle plugin but not in Buildship.
    - Automatically run tasks on Gradle project import.
    - Allow Buildship setting files to be shared between developers.
        - Buildship stores absolute locations in the `.settings/gradle.prefs` import preference file. This makes the ‘Import existing project’ wizard unusable, and makes it difficult to share a fully configured Buildship workspace.
    - Import subset of modules from a multi-module build.
    - Support renaming for eclipse projects, or deduplicate naming conflicts with a non-Gradle project.
- It should reference the relevant points from the [STS plugin documentation](https://github.com/spring-projects/eclipse-integration-gradle/wiki).
- The document should be published as a wiki page in Buildship GitHub repository

**Notify users about the migration**
- Implement a new Buildship plugin which:
    - Activates at Eclipse startup and checks the installed plugins.
    - If the STS Gradle integration plugin is installed, then a dialog is presented to the user, which
        - Informs that the STS Gradle plugin development will be discontinued,
        - Has a link to the migration guide,
        - Has a checkbox not to display the message again.
- Add the new plugin to the Buildship feature.
- Ask Pivotal to include the new plugin in the STS Gradle integration feature too.

**Implementation plan for the notification plugin**
- Add two new plugins to buildship `org.eclipse.buidship.stsmigration` and `org.eclipse.buidship.stsmigration.test`.
- Add new `org.eclipse.ui.startup` extension with the `StartupHandler` class in the plugin to ensure the plugin is always activated.
- Implement `StsMigrationDialog` to show the UI elements.
- Implement `StsMigrationService` to 1) check if STS is installed 2) checks if the notification is muted and 3) shows the dialog if necessary.
- Call `StsMigrationService.run()` from `StartupHandler`.

## Test cases
- Can detect if STS plugin is installed in Eclipse.
- STS plugin detection is activated upon startup.
- Dialog is presented to the user upon startup.
     - If the user does not check the option, then the dialog will continue to be rendered upon startup.
- User can directly click on the link in the dialog and a browser will be opened.

 -Note: Eclipse provides an API to install-uninstall bundles in the current runtime. We'll make use of this feature to provide integration tests verifying how Buildship behaves when STS is installed.

## Open issues
