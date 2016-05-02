#Buildship sets up the correct classpath for web applications

##Motivation

When building web applications, there are dependencies which should be packaged into the final archive
and others which are provided by the container. The Eclipse Web Tools use a special attribute on
each classpath entry to differentiate between these two kinds of dependencies.

Buildship currently does not set up these attributes for WTP. As a result, all dependencies
- including `providedCompile` and `testCompile` - are packaged into the `war` and deployed to the
server. There is no workaround for the user, Buildship cannot be used with WTP.

The proposed change will expose the correct classpath attributes via the Tooling API.
Buildship will consume these attributes without further interpretation. This will unblock
users from using Buildship with WTP. There will still be manual steps involved for setting up
the facets and component descriptor. But being able to use Buildship's classpath container
is a big step forward.

IntelliJ IDEA is not part of this proposal, as it already handles web apps correctly.
Dependencies are added to the correct dependency scopes (`compile`, `provided` and `test`)
and IDEA knows how to handle these scopes in the context of a web application.

###Story: Tooling API exposes classpath attributes

####API

```
interface EclipseDependency extends Dependency {
  List<ClasspathAttribute> getClasspathAttributes();
}

interface ClasspathAttribute {
  String getName();
  String getValue();
}

interface EclipseProjectDependency extends ProjectDependency, EclipseDependency {}

interface EclipseExternalDependency extends ExternalDependency, EclipseDependency {}

```

####Implementation

The classpath attributes will not yet be populated.

####Test cases

- requesting the classpath attributes on any Gradle version returns an empty list

###Story: Buildship consumes classpath attributes

####API

```
interface OmniEclipseDependency {
  List<OmniClasspathAttribute> getClasspathAttributes();
}

interface OmniClasspathAttribute {
  String getName();
  String getValue();
}

interface OmniEclipseProjectDependency extends OmniEclipseDependency {}

interface OmniEclipseExternalDependency extends OmniEclipseDependency {}

```

####Implementation

The `ClasspathContainerUpdater` will copy the classpath attributes verbatim into
the created `IClasspathEntries`.

####Test cases

- given a web project and the current Gradle version, the deployed/non-deployed
attribute is set on the `IClasspathEntries`
- if the project is not a web project or the Gradle version does not support
classpath attributes, no attributes are set

Running all the test cases from the previous story again in Buildship
might be overkill, since it is just a passive consumer of classpath attributes.
The two simple test cases above should be enough to ensure Buildship is using
the TAPI correctly.

### Story: Tooling API adds the deployed/non-deployed attributes

#### Motivation

Eclipse WTP has two way of adding dependencies to a component (e.g. a WAR or an EAR):

- inside the component descriptor
- inside the classpath, using special classpath attributes

Declaring them in the component descriptor has many disadvantages:

- it does not support source attachments
- it does not support JavaDoc
- it does not support the equivalent of "classpath containers", so if your external dependencies are in your local repository, the component file will contain user-specific paths

The `gradle eclipse` command line task currently uses the component descriptor approach with all the problems listed above (see [GRADLE-2123](https://issues.gradle.org/browse/GRADLE-2123)). On top of that, it also has problems with transitive dependency resolution, because it collects dependencies across project boundaries. This can lead to duplicate dependencies being included in the component or dependencies being added even though they were explicitly excluded in the resolution strategy.

The Tooling API should use classpath attributes instead. This will lead to two parallel implementations, which will be rectified by the next story.

#### Implementation

When the `EclipseProject` model is requested for a WTP project, the classpath entries need to be decorated with the correct attributes.

A project is a WTP project if:

- it has the `war` or `ear` plugin (in that case, auto-apply the eclipse-wtp plugin)
- or it has the `eclipse-wtp` plugin

External dependencies need to be marked depending on whether they are part of `eclipse.wtp.component.rootConfigurations` ("root dependencies") or `eclipse.wtp.component.plusConfigurations` ("lib dependencies") or neither of those ("non-dependencies")

- root dependencies are marked with `org.eclipse.jst.component.dependency=/`
- lib dependencies are marked with `org.eclipse.jst.component.dependency=/WEB-INF/lib`
- non-dependencies are marked with 'org.eclipse.jst.component.nondependency'

All project dependencies are marked as 'org.eclipse.jst.component.nondependency'. WTP currently does not support marking project dependencies as deployed via the classpath.

#### Test cases

- When neither the `war`, `ear`, nor the `eclipse-wtp` plugins are applied, no dependencies are marked with attributes
- `war` and `ear` projects contain the correct classpath attributes even if the `eclipse-wtp` plugin was not explicitly applied
- root dependencies are marked with `org.eclipse.jst.component.dependency=/`
- lib dependencies are marked with `org.eclipse.jst.component.dependency=/WEB-INF/lib`
- all other external dependencies are marked with 'org.eclipse.jst.component.nondependency'
- project dependencies are marked with 'org.eclipse.jst.component.nondependency'

### Story: Back-port correct logic to Gradle Core (GRADLE-2123)

#### Motivation

The current logic for computing the deployed/non-deployed attributes and the WTP
component descriptor is broken, as outlined in the previous story and [GRADLE-2123](https://issues.gradle.org/browse/GRADLE-2123). The fixed logic implemented in #3 should be back-ported to the `gradle eclipse` command-line task. 

#### API

The fix for GRADLE-2123 would change the meaning of `eclipse.wtp.component.plusConfigurations`,
`eclipse.wtp.component.rootConfigurations` and `eclipse.wtp.component.minusConfigurations`. They would no
longer add external dependencies to the component descriptor file. Instead, these configurations
will only be used to mark external dependencies on the classpath as deployed/non-deployed accordingly.

This might be a breaking change for users who added configurations to `eclipse.wtp.component`,
but not to `eclipse.classpath`. It is thus a good candidate for Gradle 3.0.

#### Implementation

Replace the current logic of the command line task to match the new behavior of the Tooling API. Do not add any external dependencies to the wtp component file.

#### Test cases

- no external dependencies are added to the WTP component descriptor
- the generated classpath passes the same tests as the logic in the Tooling API
