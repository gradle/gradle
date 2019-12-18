The Gradle team is excited to announce Gradle @version@.

This release finishes off 2019 with a few new features. Gradle now supports [a relocatable dependency cache](#cache), makes [compilation order between Java, Groovy and Scala classes configurable](#compilation-order) and kicks off a [new set of downloadable samples](#samples).

There are also several [bug fixes](#fixed-issues), conveniences for [Gradle plugin authors](#plugin-dev) and more.

We would like to thank the following community contributors to this release of Gradle:
[Mark Nordhoff](https://github.com/MarkNordhoff),
[Kazuki Matsuda](https://github.com/kazuki-ma),
[Andrew Malyhin](https://github.com/katoquro),
[Emmanuel Guérin](https://github.com/emmanuelguerin),
[Nicholas Gates](https://github.com/gatesn),
[Bjørn Mølgård Vester](https://github.com/bjornvester),
[Johnny Lim](https://github.com/izeye),
[Benjamin Muskalla](https://github.com/bmuskalla),
[Ian Kerins](https://github.com/isker),
[Vladimir Sitnikov](https://github.com/vlsi),
[Michael Ernst](https://github.com/mernst),
[Nelson Osacky](https://github.com/runningcode),
[Dmitriy Konopelkin](https://github.com/DeKaN),
and [Steven Crockett](https://github.com/stevencrockett).

## Upgrade Instructions

Switch your build to use Gradle @version@ by updating your wrapper:

`./gradlew wrapper --gradle-version=@version@`

See the [Gradle 6.x upgrade guide](userguide/upgrading_version_6.html#changes_@baseVersion@) to learn about deprecations, breaking changes and other considerations when upgrading to Gradle @version@.

For Java, Groovy, Kotlin and Android compatibility, see the [full compatibility notes](userguide/compatibility.html).
<!-- Do not add breaking changes or deprecations here! Add them to the upgrade guide instead. -->

<a name="cache"></a>
## Gradle's Dependency Cache can be relocated

The Gradle Dependency cache under `$GRADLE_HOME/caches/modules-2` can now be relocated to another directory or host for dependencies cached by Gradle 6.1 and later. When moved to a new location or seeded into a host image, builds using the dependency cache will not need to access the network to download artifacts or metadata if the dependencies have already been downloaded. 

Note that the cache should be primed and consumed by same Gradle version for maximum effect.
See [the documentation](userguide/dependency_resolution.html#sub:cache_copy) for more details. 

This is just one step along the way to help organizations using ephemeral CI agents to reduce the overhead of downloading dependencies during the build.

<a name="compilation-order"></a>
## Defining compilation order between Groovy, Scala and Java

Previously, the relationship between Java, Groovy and Scala compilation was hardcoded with explicit task dependencies in the same project.
Gradle assumed Groovy and Scala compilation would always depend on Java compilation. That is, `compileGroovy` and `compileScala` would directly depend on the output from `compileJava`.

These task dependencies have been remodelled using [directory properties](userguide/lazy_configuration.html#).  The relationship between compilation tasks is expressed in the task's classpath. Removing a directory property from the classpath also removes the corresponding task dependency.  This can be used to change the relationship between Java, Groovy and Scala compilation tasks.

For example, when combining Groovy and Kotlin in the same project, it was previously difficult to make Kotlin classes depend on Groovy classes.

```
tasks.named('compileGroovy') {
    // Groovy only needs the declared dependencies
    // and not the output of compileJava
    classpath = sourceSets.main.compileClasspath
}
tasks.named('compileKotlin') {
    // Kotlin also depends on the result of Groovy compilation 
    // which automatically makes it depend of compileGroovy
    classpath += files(sourceSets.main.groovy.classesDirectory)
}
```

<a name="samples"></a>
## Downloadable Gradle Samples

In addition to [tutorials and guides](https://guides.gradle.org) and an [extensive user manual](userguide/userguide.html), Gradle now provides an [index of samples](samples/index.html) to demonstrate different kinds of projects that can be built with Gradle, like [Kotlin](samples/index.html#kotlin) or [Spring boot](samples/sample_spring_boot_web_application.html). The samples also show common problems that can be solved using the Groovy or Kotlin DSL, like [adding integration tests to a Java project](samples/sample_jvm_components_with_additional_test_types.html). Each sample comes bundled with a [Gradle wrapper](userguide/gradle_wrapper.html#header), so you don't even need to have Gradle installed before trying them out.

These samples are tested with the same version of Gradle as the documentation. More samples will be added over time. If you have any suggestions for a problem or use case you think would make a good sample, [please open a new issue](https://github.com/gradle/gradle/issues/new?labels=a%3Asample%2C+from%3Acontributor&template=contributor_sample_request.md).

## Groovy compilation support for method parameter names with JDK8+ 

Gradle 6.1 supports compiling Groovy code and including [method parameter names](https://docs.oracle.com/javase/tutorial/reflect/member/methodparameterreflection.html).

This was contributed by [Andrew Malyhin](https://github.com/katoquro).

<a name="plugin-dev"></a>
## Improvements for plugin authors

### Finalize property value only when the value is queried

In previous Gradle releases, certain Gradle types, such as [`Property`](javadoc/org/gradle/api/provider/Property.html) or [`ConfigurableFileCollection`](javadoc/org/gradle/api/file/ConfigurableFileCollection.html),
provided a `finalizeValue()` method that eagerly calculated the final value for a property and prevented further changes.

When a task starts running, Gradle automatically finalizes task properties of these types, so that the same value is seen by the task's actions and Gradle's build caching/up-to-date checks. This also avoids calculating the property value multiple times, which can sometimes be expensive. Plugins can also use `finalizeValue()` to finalize other properties, such as a property of a project extension, just prior to querying the value.

In this release, these types gain a new [`finalizeValueOnRead()`](javadoc/org/gradle/api/provider/HasConfigurableValue.html#finalizeValueOnRead--) method. This method is similar to `finalizeValue()`, except that the final value is calculated when the value is queried rather than immediately. Plugins can use this method when a property value may be expensive to calculate or when the value may not have been configured to ensure that all consumers of the property see the same, final, value from that point onwards.

Please see the [user manual](userguide/lazy_configuration.html#unmodifiable_property) for more details.

### New managed property types

Gradle 5.5 introduced the concept of a [_managed property_ for tasks and other types](userguide/custom_gradle_types.html#managed_properties), where Gradle provides an implementation of the getter 
and setter for an abstract property defined on a task, project extension, or other custom type.
This simplifies plugin implementations by removing a bunch of boilerplate.

In this release, it is possible for a task or other custom type to have an abstract read-only property of type [`DomainObjectSet<T>`](javadoc/org/gradle/api/DomainObjectSet.html).

Please see the [user manual](userguide/custom_gradle_types.html#managed_properties) for more details.

### New factory methods

The [`ObjectFactory`](javadoc/org/gradle/api/model/ObjectFactory.html) type, which plugins and other custom types use to create instances of various useful types, has several new factory methods to create certain Gradle types that could only be created using internal APIs in previous releases: 

- The [`polymorphicDomainObjectContainer()`](javadoc/org/gradle/api/model/ObjectFactory.html#polymorphicDomainObjectContainer-java.lang.Class-) method to create [`ExtensiblePolymorphicDomainObjectContainer<T>`](javadoc/org/gradle/api/ExtensiblePolymorphicDomainObjectContainer.html) instances.
- The [`namedDomainObjectSet()`](javadoc/org/gradle/api/model/ObjectFactory.html#namedDomainObjectSet-java.lang.Class-) method to create [`NamedDomainObjectSet<T>`](javadoc/org/gradle/api/NamedDomainObjectSet.html) instances.
- The [`namedDomainObjectList()`](javadoc/org/gradle/api/model/ObjectFactory.html#namedDomainObjectList-java.lang.Class-) method to create [`NamedDomainObjectList<T>`](javadoc/org/gradle/api/NamedDomainObjectList.html) instances.

Please see the [user manual](userguide/custom_gradle_types.html#collection_types) for more details.

## Tooling API: `TestLauncher` can run specific `Test` task tests

The `TestLauncher` interface in the Tooling API could already launch tests by specifying the name of the test classes or methods; however, if there are multiple `Test` tasks, then all `Test` tasks would be executed. 

For IDEs, developers usually want to execute only one task at a time. Gradle 6.1 introduces a new API to execute tests with specific `Test` task using the `withTaskAndTestClasses()` and `withTaskAndTestMethods()` methods.

## Fixed issues

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.

## External contributions

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Reporting Problems

If you find a problem with this release, please file a bug on [GitHub Issues](https://github.com/gradle/gradle/issues) adhering to our issue guidelines.
If you're not sure you're encountering a bug, please use the [forum](https://discuss.gradle.org/c/help-discuss).

We hope you will build happiness with Gradle, and we look forward to your feedback via [Twitter](https://twitter.com/gradle) or on [GitHub](https://github.com/gradle).
