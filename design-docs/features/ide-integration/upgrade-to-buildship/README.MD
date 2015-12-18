# Provide smooth upgrade from STS Gradle to Buildship

- [ ] Rename labels contributed by the STS plugin
- [ ] Implement missing Buildship features
- [ ] Provide document with migration steps
- [ ] Detect STS Gradle installation and notify the user

# Stories

### Rename labels contributed by the STS plugin

- The common UI elements have the same labels:
    - Wizards: `New > Gradle > Gradle Project`, and `Import > Gradle > Gradle Project`
    - Gradle context menu
    - Gradle Tasks view: `Gradle  > Gradle Tasks`
As a result it is hard to tell which UI contribution belongs to what plugin

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


### Implement missing Buildship features

The following features are available in the STS Gradle but not in Buildship
- Automatically run tasks after the import
- Import a subset of modules from a build
- Optionally regenerate project descriptors (the `.project` and the `.classpath` file) when importing the modules
- Share import settings
    - Buildship stores absolute locations in the `.settings/gradle.prefs`  import preference file. This makes the
  ‘Import existing project’ wizard unusable
- Add renaming support for the projects
    - sync the project names from the Gradle model
    - resolve name conflicts if the name is taken in the workspace by a non-Gradle project

##### Estimate

-

##### Implementation

-

##### Test cases

-

##### Open issues

- Do we have to implement all of the features or is there a few we can exclude?
- We might want to explode this story to a set of smaller ones


### Provide document with migration steps

Create a tutorial document with steps how switch to Buildship.

##### Estimate

-

##### Implementation

-

##### Test cases

-

##### Open issues

- We need a good example project to provide such docuement.



### Detect STS Gradle installation and notify the user

Provide a feature to query the list of installed Eclipse plugins. If STS Gradle is installed then notify the user about it.
The notification should contain a link to the migration document.

##### Estimate

-

##### Implementation

-

##### Test cases

-

##### Open issues

-
