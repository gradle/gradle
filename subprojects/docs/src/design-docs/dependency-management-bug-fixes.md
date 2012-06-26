This feature is really a bucket for key things we want to fix in the short-term for Dependency Management, many of which have require
(or have the potential for) a strategic solution.

As this 'feature' is a list of bug fixes, this feature spec will not follow the usual template.

# Correct handling of packaging and dependency type declared in poms

* GRADLE-2188: Artifact not found resolving dependencies with packaging/type "orbit"

## Description
Our engine for parsing Maven pom files is borrowed from ivy, and assumes the 'packaging' element equals the artifact type, with a few exceptions (ejb, bundle, eclipse-plugin, maven-plugin).
This is different from the way Maven does the calculation, which is:
* Type defaults to 'jar' but can be explicitly declared.
* Maven maps the type to an [extension, classifier] combination using some hardcoded rules. Unknown types are mapped to [type, ""].
* To resolve the artefact, maven looks for an artefact with the given artifactId, version, classifier and extension.

## Strategic solution

At present, our model of an Artifact is heavily based on ivy; for this fix we can introduce the concept of mapping between our internal model and a repository-centric
artifact model. This will be a small step toward an independent Gradle model of artifacts, which then maps to repository specific things link extension, classifier, etc.

## User visible changes

* We should successfully resolve dependencies with packaging = 'orbit'
* We should emit a deprecation warning for cases where packaging->extension mapping does not give same result as type->extension mapping

## Integration test coverage

* Add some more coverage of resolving dependencies with different Maven dependency declarations. See MavenRemotePomResolutionIntegrationTest.
    * packaging = ['', 'pom', 'jar', 'war', 'eclipse-plugin'] ('orbit' can be a unit test)
    * type = ['', 'jar', 'war']

## Implementation approach

* Map type->extension+classifier when resolving from Maven repositories. Base this mapping on the Maven3 rules.
* Retain current packaging->extension mapping for specific packaging types, and add 'orbit' as a new exception to this mapping.
* Emit a deprecation warning where packaging != jar and use of packaging->extension mapping gives a different result to type->extension mapping.
* In 2.0, we will remove the packaging->extension mapping and the deprecation warning
