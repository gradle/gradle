# Story: Detect STS Gradle installation and notify the user

Query the list of installed Eclipse plugins, and if STS Gradle is installed then notify the user about it being superceded by Buildship.
The notification should contain a link to a document outlining the steps required for migration, including uninstalling the STS plugin.

## Estimate

- Migration guide - 1 day
- Detect STS installation - 2 days

## Implementation

- Write a migration guide for STS Gradle plugin users explaining how to transition to Buildship.
- Document features that are available in the STS Gradle plugin but not in Buildship.
    - Automatically run tasks on Gradle project import.
    - ~~Optionally regenerate project descriptors (the `.project` and the `.classpath` file) when importing the modules.~~
        - This [feature](https://bugs.eclipse.org/bugs/show_bug.cgi?id=481399) is already implemented and will be released as part of Buildship 1.0.8.
    - Allow Buildship setting files to be shared between developers
        - Buildship stores absolute locations in the `.settings/gradle.prefs` import preference file. This makes the ‘Import existing project’ wizard unusable, and makes it difficult to share a fully configured Buildship workspace
    - Import subset of modules from a multi-module build
    - Support renaming for eclipse projects, or deduplicate naming conflicts with a non-Gradle project.
- Query the list of installed Eclipse plugins when Eclipse is started up.
    - If only Buildship is installed, do nothing.
    - If Buildship and the STS Gradle plugin are detected, render a message for the user.
        - Inform that the STS Gradle plugin is discontinued and that Pivotal will stop supporting it.
        - Add a link to the migration guide.
        - Add checkbox 'Don't show this message again'

## Test cases

- Can query STS plugin when it's part of the Eclipse runtime
- Can query STS plugin when it's not part of the Eclipse runtime
- STS plugin detection is activated upon startup
     - if user checked the 'Don't show this message again' option, then the message won't show up for the current Eclipse instance
- User can navigate to the migration guide

## Open issues

- In what form do we want to provide the migration guide? It could be a separate website as well as a contribution to the Eclipse help system. Or both at the same time (external web page embedded into Eclipse's help system).
