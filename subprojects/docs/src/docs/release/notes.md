
## New and noteworthy

Here are the new features introduced in Gradle 1.1-rc-1

### Some feature

A paragraph about the feature.

#### Some detail

Some details about the feature.

## Upgrading from Gradle 1.0

Please let us know if you encounter any issues during the upgrade to Gradle 1.1-rc-1, that are not listed below.

### Deprecations

#### Statement Labels

As in Java, statement labels are rarely used in Groovy. The following example shows a frequent pitfall where a
statement label is erroneously used in an attempt to configure an object:

<pre>
task foo {
    dependsOn: bar // does nothing; correct is 'dependsOn bar' or 'dependsOn = [bar]'
}
</pre>

To prevent such mistakes, the usage of statement labels in build scripts has been deprecated.

#### DSL

##### `Project.fileTree(Object)` - Removal of incorrect `@deprecation` tag

The `Project.fileTree(Object)` method was incorrectly annotated with the `@deprecated`
Javadoc tag in Gradle 1.0-milestone-8. This method has not been deprecated and the Javadoc tag has been removed.

##### `Project.fileTree(Closure)` - Addition of `@deprecation` tag

The `Project.fileTree(Closure)` method was deprecated in Gradle 1.0-milestone-8. The method was not
annotated with the `@deprecated` javadoc tag at that time. This has been added for this release.

### Potential breaking changes

##### `idea.project.jdkName`

We've decided to change the IDEA plugin's default JDK name. The new default is now smarter. Without this change,
many users had to configure the JDK name explicitly in the builds or manually tweak the JDK name in IDEA after running
the `gradle idea` task. The current default uses the Java version that Gradle runs with.

Although we believe the new default is much better for majority of users, there might be some builds out there
that preferred the old default. If you happen to prefer the old default (`1.6`) please configure
that explicitly in your build via [idea.project.jdkName](http://gradle.org/docs/current/dsl/org.gradle.plugins.ide.idea.model.IdeaProject.html#org.gradle.plugins.ide.idea.model.IdeaProject:jdkName)

## Fixed Issues

The list of issues fixed between 1.0 and 1.1-rc-1 can be found [here](http://issues.gradle.org/sr/jira.issueviews:searchrequest-printable/temp/SearchRequest.html?jqlQuery=fixVersion+in+%28%221.1-rc-1%22%29+ORDER+BY+priority&tempMax=1000).
