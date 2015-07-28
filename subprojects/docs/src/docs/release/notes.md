Gradle 2.6 is an exciting release with some shiny new features and many under the hood improvements.

The new support for the [Play](https://www.playframework.com) framework is exciting for many reasons.
If you build Play applications, you can now do so with Gradle and take advantage of all of Gradle's features and capabilities.
Play developers with more sophisticated dependency management or orchestration requirements in particular may find building
Play apps with Gradle to be particularly appealing.

The Play support is also an exciting development for non-Play users as well.
The development time reloading implementation leverages the continuous build capabilities in Gradle 2.5 to be “change aware”.
This mechanism will eventually be generally available to be used by integrations with other web frameworks or indeed any
long running “thing” being built.

The new “Gradle TestKit” provides an official mechanism for testing custom plugins which will greatly improve the Gradle plugin development experience.
This initial version of the TestKit focuses on executing real builds and asserting the result (i.e. functional testing).
Expect the TestKit to improve and expand over coming releases.

## New and noteworthy

Here are the new features introduced in this Gradle release.

### Play Framework Support (i)

Gradle can now build [Play](https://www.playframework.com/) applications for Play version 2.3.x and 2.4.x.
The new `play` plugin allows users to build, test, run and package Play applications.

It includes support for:

* Compiling Scala and Java controllers, tests, and model classes
* Processing routes files
* Processing Twirl templates
* Compiling CoffeeScript assets
* Minifying JavaScript assets
* Running tests with JUnitRunner
* Running applications in development mode with hot reloading
* Staging and creating Play distribution packages

Compatibility with Play 2.4.x is limited.
The `play` plugin does not work with a few new build-related features in 2.4.
Specifically, Gradle does not allow you to configure reverse routes or use "injected" routes generators.
Future releases will add support for these features as well as other features and versions of the Play Framework.

When running a Play application as part of a [continuous build](userguide/continuous_build.html), changes to the source are reflected in the running app automatically… no restart required.
This facilitates a fast development cycle.
The mechanism empowering this “hot reloading” will be made generally available to other types of applications and services in future versions of Gradle.

See the [User Guide](userguide/play_plugin.html) as well as the sample builds delivered with the Gradle distribution for more information on using the `play` plugin.

### Support for functionally testing Gradle plugins

This release brings the initial version of, the long awaited, official support for functionally testing Gradle plugins.
That is, being able to programmatically execute a contrived build leveraging a plugin under development as part of the plugin's build.
This functionality is being delivered as part of the new Gradle TestKit.

Many community members stepped up and filled the void created by the absence of a built in way to do this.
Notably, the folks at [Netflix](http://netflix.com/) contributed the very popular [Nebula Test](https://github.com/nebula-plugins/nebula-test) as part of their [Nebula Project](https://github.com/nebula-plugins).
The functionality provided by this and other similar projects will over time be rolled into the Gradle TestKit.

The following example demonstrates the use of the TestKit API in a test method based on the test framework [Spock](https://code.google.com/p/spock/):

    def "hello world task prints hello world"() {
        given:
        buildFile << """
            task helloWorld {
                doLast {
                    println 'Hello world!'
                }
            }
        """

        when:
        def result = GradleRunner.create()
            .withProjectDir(testProjectDir.root)
            .withArguments('helloWorld')
            .build()

        then:
        result.standardOutput.contains('Hello world!')
        result.task(":helloWorld").outcome == SUCCESS
    }

See the [new Gradle TestKit user guide chapter](userguide/test_kit.html) for more information.

### Tooling API TestLauncher (i)

This releases introduces a new [`TestLauncher`](javadoc/org/gradle/tooling/TestLauncher.html) interface for launching tests using the [Tooling API](userguide/embedding.html).

The following is an example of using the new API…

		ProjectConnection connection = GradleConnector.newConnector()
		   	.forProjectDirectory(new File("someFolder"))
		   	.connect();

		try {
		   //run tests
		   connection.newTestLauncher()
			 .withTests(descriptor1, descriptor2)
             .launcher.withJvmTestClasses("example.MyTest")
			 .addProgressListener(new MyTestListener(), EnumSet.of(OperationType.TEST))
		     .setStandardOutput(System.out)
		     .run();
		} finally {
		   connection.close();
	    }

See the Javadoc for [`ProjectConnection`](dsl/org.gradle.tooling.ProjectConnection.html) and [`TestLauncher`](dsl/org.gradle.tooling.TestLauncher.html) for more information on using the new TestLauncher API.

### Rule based model configuration reporting improvements (i)

Gradle 2.5 brings significant usability enhancements to the new [Rule based model configuration mechanism](userguide/new_model.html),
through better reporting.

The [in-built “Model report”](userguide/new_model.html#N18025) now exposes much more information about the build model, including:

* The Java type of each model element
* A string representation of the value of each model element
* Which rule created each model element
* Which rules were involved in configuring a model element and the order in which they were applied

The model report makes it much easier to see the effective configuration of the build, and comprehend how it came to be.
Future improvements to the model report will include:

* Improved names/identifiers for rules
* Visualisation of dependencies between elements
* Alternative formats that provide more control over level of detail
* Greater coverage of the total build model

In addition to the model report improvements, rule “binding failure” error messages have also been improved.
A binding failure occurs when the declared subject or any of the inputs for a given rule cannot be found when the rule is required.
Such a failure is fatal to the build.

The new format presents vital debugging information such as:

* Which inputs or subjects could not be bound.
* Where, in the build script, the missing rule dependency was identified.
* The model path to the model element with the failure.
* Which method parameter, on a method rule, could not be bound.
* Suggestions as to which inputs/subjects could be used to successfully bind.

e.g.
Consider the following rule configuration which is invalid because of an unsatisfied input: `bar`.

	class Rules extends RuleSource {
		@Model
		Integer foo(@Path("bar") Integer bar) {
			return 22
		}
	}

	apply type: Rules

The resulting error message gives some clear insight into what went wrong and why:


	The following model rules could not be applied due to unbound inputs and/or subjects:

	  Rules#foo
		inputs:
		  - bar Integer (parameter 1) [*]

	[*] - indicates that a model item could not be found for the path or type.

In the above example, the error occurred due to there being no model element at path `bar`.

This is an area of active development and will continue to improve and evolve in coming Gradle releases.

### Support for verifying Gradle wrapper distribution download against SHA-256 hash

It is now possible to verify the integrity of the Gradle distribution used by the [Gradle wrapper](userguide/gradle_wrapper.html) against a known checksum.
This provides an extra level of assurance that the Gradle runtime is exactly the intended version.

To enable verification you need only specify a `distributionSha256Sum` property in your project's `gradle-wrapper.properties` file.

    distributionSha256Sum=e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855

Please see section [Verification of downloaded Gradle distributions](userguide/gradle_wrapper.html#sec:verification) of the User Guide for more information.

This feature was contributed by [Dominik Schürmann](https://github.com/dschuermann).

## Fixed issues

## Deprecations

Features that have become superseded or irrelevant due to the natural evolution of Gradle become *deprecated*, and scheduled to be removed
in the next major Gradle version (Gradle 3.0). See the User guide section on the “[Feature Lifecycle](userguide/feature_lifecycle.html)” for more information.

The following are the newly deprecated items in this Gradle release. If you have concerns about a deprecation, please raise it via the [Gradle Forums](http://discuss.gradle.org).

### Deprecation of methods on (incubating) software model

* `BinarySpec.getSource()` has been deprecated and replaced with `BinarySpec.getInputs()`.
* `ComponentSpec.getSource()` has been deprecated and replaced with `ComponentSpec.getSources()`.

These incubating methods will be removed in the next minor version.

## Potential breaking changes

### Removal of methods on (incubating) software model

* Removed `FunctionalSourceSet.copy()`

### Updated default Scala Zinc compiler version

The default version of the Scala Zinc compiler has changed from 0.3.5.3 to 0.3.7.

### Changes to source set handling of binaries

Binaries now distinguish between source sets that are specific to them (owned source set) and external source sets that are also required to build them.

* `BinarySpec.sources()` now takes an `Action` that operates on a `ModelMap<LanguageSourceSet>` instead of a `PolymorphicDomainObjectContainer`. Source sets defined here are specific to the binary.
* Added `BinarySpec.getSources()` that returns only the sources specific to the binary.
    * Note: this method shadows access to `ComponentSpec.getSources()` when used in a nested `binaries` block.
* Added `BinarySpec.getInputs()` that contains all the source sets needed to build the binary, including the ones specific to the binary and external source sets (e.g. inherited from the binary's parent component).
* Removed `BinarySpec.source(Object)`: to add an existing sourceSet to a binary, use `BinarySpec.getInputs().add()`.
* `@Managed` models are no longer permitted to have setter methods for members of type `ManagedSet`.

## External contributions

We would like to thank the following community members for making contributions to this release of Gradle.

* [Ben Blank](https://github.com/benblank) - Allow CopySpec.filter() to remove lines
* [Steve Ebersole](https://github.com/sebersole) - Support for passing arbitrary arguments to FindBugs tasks
* [Malte Finsterwalder](https://github.com/finsterwalder) - Fixed resolving of references to `${parent.artifactId}` in POM files (GRADLE-3299)
* [Ethan Hall](https://github.com/ethankhall) - Update Scala Zinc compiler version to work with Java 8 (GRADLE-3319)
* [Jordan Jennings](https://github.com/jordanjennings) - Documentation improvements
* [Roy Kachouh](https://github.com/roykachouh) - Fix for Application plugin script generation in projects with alphanumeric names
* [Zoltán Kurucz](https://github.com/qzole) - Documentation improvements
* [Yu Lu](https://github.com/yulucodebase) - Logging improvements
* [Amit Portnoy](https://github.com/amitport) - Minor fix in LifecycleBasePlugin
* [Harald Schmitt](https://github.com/surfing) - Fixed mixup in credentials design spec
* [Sebastian Schuberth](https://github.com/sschuberth) - Documentation improvements
* [Dominik Schürmann](https://github.com/dschuermann) - Support for verifying Gradle wrapper distribution download against SHA-256 hash
* [Andrew Shu](https://github.com/talklittle) - Documentation improvements

We love getting contributions from the Gradle community. For information on contributing, please see [gradle.org/contribute](http://gradle.org/contribute).

## Known issues

Known issues are problems that were discovered post release that are directly related to changes made in this release.
