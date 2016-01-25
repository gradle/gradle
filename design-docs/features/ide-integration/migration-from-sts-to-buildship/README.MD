# Story: Detect STS Gradle installation and notify the user

Query the list of installed Eclipse plugins, and if STS Gradle is installed then notify the user about it being superceded by Buildship.
The notification should contain a link to a document outlining the steps required for migration, including uninstalling the STS plugin.

## Estimate

-

## Implementation

- Write a migration guide for STS Gradle plugin users explaining how to transition to Buildship.
- Document features that are available in the STS Gradle plugin but not in Buildship.
    - Automatically run tasks on Gradle project import.
    - Optionally regenerate project descriptors (the `.project` and the `.classpath` file) when importing the modules. _TODO:_ We worked on this. When is it going to be released?
    - Allow Buildship setting files to be shared between developers
        - Buildship stores absolute locations in the `.settings/gradle.prefs` import preference file. This makes the ‘Import existing project’ wizard unusable, and makes it difficult to share a
        fully configured Buildship workspace
    - Support renaming for eclipse projects, or deduplicate naming conflicts with a non-Gradle project.
- Query the list of installed Eclipse plugins when Eclipse is started up.
    - If only Buildship is installed, do nothing.
    - If Buildship and the STS Gradle plugin are detected, render a message for the user.
        - Inform that the STS Gradle plugin is discontinued and that Pivotal will stop supporting it.
        - Add a link to the migration guide.

## Test cases

-

## Open issues

-




