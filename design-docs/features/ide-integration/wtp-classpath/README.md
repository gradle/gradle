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
  String getKey();
  String getValue();
}

interface EclipseProjectDependency extends ProjectDependency, EclipseDependency {}

interface EclipseExternalDependency extends ExternalDependency, EclipseDependency {}

```

####Implementation

The classpath attributes will not yet be populated.

####Test cases

- requesting the classpath attributes on the current Gradle version returns an empty list
- requesting the classpath attributes on any previous Gradle version throws an `UnsupportedMethodException`

###Story: Tooling API adds the deployed/non-deployed attributes

####API

No change

####Implementation

There is existing logic setting the deployed(`org.eclipse.jst.component.dependency`)
and non-deployed(`org.eclipse.jst.component.nondependency`) attributes in the
`EclipseWtpPlugin`. The logic is contained in a `classpath.file.whenMerged` hook.
It should be extracted and reused in the `EclipseModelBuilder`.
To make sure the `WtpComponent` is available, we will automatically apply the
`EclipseWtpPlugin` in the `EclipseModelBuilder` when the `war` or `ear` plugins
are applied.

####Test cases

- The `EclipseWtp*IntegrationTest` contain test logic for the deployed/non-deployed attribute.
The tooling API should pass the same tests.
- When neither the `war`, `ear`, nor the `eclipse-wtp` plugins are applied, no dependencies are marked with attributes

###Story: Buildship consumes classpath attributes

####API

```
interface OmniEclipseDependency {
  List<OmniClasspathAttribute> getClasspathAttributes();
}

interface OmniClasspathAttribute {
  String getKey();
  String getValue();
}

interface OmniEclipseProjectDependency extends OmniEclipseDependency {}

interface OmniEclipseExternalDependency extends OmniEclipseDependency {}

```

####Implementation

If the connected Gradle version does not support classpath attributes, the
`OmniEclipseDependency` will return an empty list instead of throwing an exception.

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

###Story: All dependencies are added via the classpath (GRADLE-2123)

The current logic for computing the deployed/non-deployed attributes and the WTP
component descriptor is broken:

- dependencies are declared both in the classpath and in the WTP component descriptor
- this makes the component descriptor non-portable as it contains absolute paths
to the repository
- dependencies in the component descriptor have neither JavaDoc nor sources
- when the resolution of the component descriptor entries and the classpath entries
select a different version of the same library, both are added to the project

To fix all these issues, dependencies should only be declared in the classpath
and marked with the correct deployed/non-deployed attributes.

####API

The fix for GRADLE-2123 would change the meaning of `eclipse.wtp.component.plusConfigurations`,
`eclipse.wtp.component.rootConfigurations` and `eclipse.wtp.component.minusConfigurations`. They would no
longer add dependencies to the component descriptor file. Instead, these configurations
will only be used to mark dependencies on the classpath as deployed/non-deployed accordingly.

This might be a breaking change for users who added configurations to `eclipse.wtp.component`,
but not to `eclipse.classpath`.

####Implementation

All of the logic adding dependencies to the component descriptor can be removed.
Instead, the deployed dependencies need to be resolved and their corresponding
classpath entry marked as deployed. All unmatched dependencies need to be marked as
non-deployed.

####Test cases

- no dependencies are added to the WTP component descriptor
- root dependencies are marked with `org.eclipse.jst.component.dependency=/`
- lib dependencies are marked with `org.eclipse.jst.component.dependency=/WEB-INF/lib`
- all other dependencies are marked with 'org.eclipse.jst.component.nondependency'

####Open Issues

We don't have any real world WTP projects to check our assumptions about whether
the stories above will solve all the deployment-related problems. Acquiring such
examples should have high priority.
