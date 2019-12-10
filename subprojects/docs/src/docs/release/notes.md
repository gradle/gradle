The Gradle team is excited to announce Gradle @version@.

This release features [1](), [2](), ... [n](), and more.

We would like to thank the following community contributors to this release of Gradle:
<!--
Include only their name, impactful features should be called out separately below.
 [Some person](https://github.com/some-person)
-->

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

## Gradle Dependency Cache can be relocated

With this release, the Gradle Dependency cache, that is the content under `$GRADLE_HOME/caches/modules-2`, can be relocated, for data cached by Gradle version 6.1 and later.
This enables its copy from host to host, allowing to fully leverage all the cached information: artifacts downloaded and metadata parsed.

Note that priming the cache and consuming it needs to use the same Gradle version for maximum effect.
See [the documentation](userguide/dependency_resolution.html#sub:cache_copy) for details on this.

This is one step in helping out ephemeral CI setups where host images can be seeded with dependency cache content, reducing the amount of downloads during the build.

## Groovy compilation support for method parameter names with JDK8+ 

Gradle now supports compiling Groovy code with [method parameter names](https://docs.oracle.com/javase/tutorial/reflect/member/methodparameterreflection.html).

This was contributed by [Andrew Malyhin](https://github.com/katoquro).

## Features for Gradle tooling providers

### `TestLauncher` can select specific methods

The `TestLauncher` interface in the Tooling API is capable of launching tests by specifying the name of the test classes or methods. If there are multiple test tasks contain those test classes/methods, then all tasks are executed. This is not ideal for IDEs: developers usually want to execute only one test variant at the time. To overcome this, Gradle 6.1 introduces the `withTaskAndTestClasses()` and `withTaskAndTestMethods()` methods.

## Improvements for plugin authors

### Finalize property value only when the value is queried

In previous Gradle releases, certain Gradle types, such as [`Property`](javadoc/org/gradle/api/provider/Property.html) or [`ConfigurableFileCollection`](javadoc/org/gradle/api/file/ConfigurableFileCollection.html),
provide a `finalizeValue()` method, which calculates a final value for the object and prevents further changes.
Gradle automatically finalizes task properties of these types when the task starts running, so that the same value is seen by everything that queries the property value, such as Gradle's build caching
or the task action. This also avoids calculating the property value multiple times, which can be expensive. Plugins can use this method to finalize other properties, for example
a property of a project extension, prior to querying the property value.

In this release, these types gain a new [`finalizeValueOnRead()`](javadoc/org/gradle/api/provider/HasConfigurableValue.html#finalizeValueOnRead--) method. This method is similar to `finalizeValue()`, except that the final
value is calculated on demand when the value is queried, rather than eagerly when `finalizeValue()` is called. Plugins can use this method when a property value may be expensive to calculate or when the
value may not have been configured, and can still ensure that all consumers of the property see the same, final, value from that point onwards.

Please see the [user manual](userguide/lazy_configuration.html#unmodifiable_property) for more details.

### New managed property types

Gradle 5.5 introduced the concept of a [_managed property_ for tasks and other types](userguide/custom_gradle_types.html#managed_properties), where Gradle provides an implementation of the getter 
and setter for an abstract property defined on a task, project extension, or other custom type.
This simplifies plugin implementation by removing a bunch of boilerplate.

In this release, it is possible for a task or other custom type to have an abstract read-only property of type [`DomainObjectSet<T>`](javadoc/org/gradle/api/DomainObjectSet.html).

Please see the [user manual](userguide/custom_gradle_types.html#managed_properties) for more details.

### New factory methods

The [`ObjectFactory`](javadoc/org/gradle/api/model/ObjectFactory.html) type, which plugins and other custom types use to create instances of various useful types, has several new factory methods.
These metehods create certain Gradle types that could only be created using internal APIs in previous releases: 

- The [`polymorphicDomainObjectContainer()`](javadoc/org/gradle/api/model/ObjectFactory.html#polymorphicDomainObjectContainer-java.lang.Class-) method to create [`ExtensiblePolymorphicDomainObjectContainer<T>`](javadoc/org/gradle/api/ExtensiblePolymorphicDomainObjectContainer.html) instances.
- The [`namedDomainObjectSet()`](javadoc/org/gradle/api/model/ObjectFactory.html#namedDomainObjectSet-java.lang.Class-) method to create [`NamedDomainObjectSet<T>`](javadoc/org/gradle/api/NamedDomainObjectSet.html) instances.
- The [`namedDomainObjectList()`](javadoc/org/gradle/api/model/ObjectFactory.html#namedDomainObjectList-java.lang.Class-) method to create [`NamedDomainObjectList<T>`](javadoc/org/gradle/api/NamedDomainObjectList.html) instances.

Please see the [user manual](userguide/custom_gradle_types.html#collection_types) for more details.

## Promoted features
Promoted features are features that were incubating in previous versions of Gradle but are now supported and subject to backwards compatibility.
See the User Manual section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the features that have been promoted in this Gradle release.

<!--
### Example promoted
-->

## Fixed issues

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.

## External contributions

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](https://gradle.org/contribute).

## Reporting Problems

If you find a problem with this release, please file a bug on [GitHub Issues](https://github.com/gradle/gradle/issues) adhering to our issue guidelines.
If you're not sure you're encountering a bug, please use the [forum](https://discuss.gradle.org/c/help-discuss).

We hope you will build happiness with Gradle, and we look forward to your feedback via [Twitter](https://twitter.com/gradle) or on [GitHub](https://github.com/gradle).
