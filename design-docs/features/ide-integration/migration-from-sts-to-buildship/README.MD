# Provide a smooth upgrade and assistance when a user switches from STS Gradle to Buildship

- [ ] Rename labels contributed by the STS plugin
- [ ] Detect STS Gradle installation and notify the user
- [ ] Document steps for migration from STS to Buildship
- [ ] Implement missing Buildship features

# Stories

### Story: Rename labels contributed by the STS plugin

- The common UI elements have the same labels:
    - Wizards: `New > Gradle > Gradle Project`, and `Import > Gradle > Gradle Project`
    - Gradle context menu
    - Gradle Tasks view: `Gradle  > Gradle Tasks`
As a result it is hard to tell which UI contribution belongs to what plugin. Ideally, the STS plugin will use labels that are clearly distinct from those provided by Buildship. Alternatively, Buildship could provide de-duplicated labels when the STS plugin is detected.

##### Estimate

-

##### Implementation

We already contacted the STS project maintainers. The feedback was that they are willing to change them, but a next
release is scheduled March 2016.

##### Test cases

-

##### Open issues

- Do we want to wait until March 2016 to have it implemented?
- Should we create a [feature patch](http://eclipsesource.com/blogs/2012/07/30/patching-your-own-eclipse-ide)
  to modify the labels on our own?

### Story: Detect STS Gradle installation and notify the user

Query the list of installed Eclipse plugins, and if STS Gradle is installed then notify the user about it being superceded by Buildship.
The notification should contain a link to a document outlining the steps required for migration, including uninstalling the STS plugin.

##### Estimate

-

##### Implementation

-

##### Test cases

-

##### Open issues

-

# Open issues: STS features that are missing from Buildship

The following features are available in the STS Gradle but not in Buildship.

- Automatically run tasks on Gradle project import
- Optionally regenerate project descriptors (the `.project` and the `.classpath` file) when importing the modules
- Allow Buildship setting files to be shared between developers
    - Buildship stores absolute locations in the `.settings/gradle.prefs`  import preference file. This makes the ‘Import existing project’ wizard unusable, and makes it difficult to share a fully configured Buildship workspace
- Support renaming for eclipse projects, or deduplicate naming conflicts with a non-Gradle project

Note that not all of these features necessarily need to be implemented by Buildship in order to replace STS.




