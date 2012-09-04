# What is this?

The build comparison functionality allow users to compare two builds functionally, identifying differences in the observable outcomes. There are three primary use cases that this functionality will serve as that basis for:

1. Changing the version of Gradle used to build a project (typically a new version, i.e. **upgrading**)
2. **Migrating** a project that is built with another tool (e.g. Ant, Maven or anything else) to be built with Gradle
3. **Testing a change** to a Gradle project's configuration by comparing before and after the change

The above list is in order of importance.

By identifying the differences, or hopefully confirming that there are no differences, between two builds the user can upgrade/migrate/change with more confidence and reduced risk.

# Components

The idea can be conceptualised into two separate components; the build outcomes, and the comparison of two sets of outcomes.

## Build Outcomes

A build outcome is an intentionally general concept. The most obvious build outcome will often be the creation of some kind of artifact (e.g. creation of a zip file),
but there are other interesting outcomes that users may want information about. Some examples are:

* Executing tests (where the same classes tested? where the same tests executed? where the results the same?)
* Analysing code (where the same classes analysed? was the configuration the same? where the results the same?)
* A “build run” - e.g. a clean build that compiles, analyses, tests (does this take an equivalent amount of time?)

These are not necessarily outcomes we will support. They are provided as examples of less obvious outcomes that we may support at some time.

## Comparison

The comparison of two builds is really a comparison of their comparable outcomes. 

The goal may not be absolute equivalence but, explainable difference. Some examples of explainable difference are:

* Embedded timestamps in outputs (e.g. Groovy classfiles, test result files)
* Environmental context (e.g. system properties in test result files)
* Acceptable path differences

The information display will not be focussed on a binary yes/no, but on illuminating what the differences are.

# Modelling Build Outcomes

To support comparing outcomes from two different build systems the concept of a build outcome is that it is agnostic to the build system that produced it. For example, an Ant build and a Gradle build can produce a zip file that is logically equivalent (i.e. is intended to represent the same “project”) and it must be possible to compare these outcomes without regard of the build system used.

## Identifying the outcomes of a build

For Gradle projects, we can identify build outcomes without user intervention by using the Tooling API to inspect the model (specifics on this later in the document). For other types of build tools we may do less automatically. That is, the user may need to specify what the outcomes are.

Even for the scenario where we can identify the outcomes, we _may_ still require a level of user intervention. For example, the user may not wish to compare test execution between two builds because they are irreconcilably different and this has been accepted. This should be a function of the comparison and not of the input models.

## Idenitifying comparable outcomes

Given two sets of outcomes, which outcomes are meaningfully comparable to each other needs to be identified. As there will always be a Gradle build involved, the strategy for _linking_ outcomes from each bold can center around task paths.

When comparing two Gradle builds, it is reasonable to expect that comparable outcomes can be identified. When comparing a Gradle build with something else it becomes much more difficult. There are two strategies, that can be used together:

1. Infer from file system locations
2. Have the user map the other outcomes to the Gradle outcomes based on task paths

Note that not all Gradle outcomes have a 1 to 1 relationship with tasks. For example, published artifacts do not necessarily have this relationship.

# The “result”

It may not be desirable to “fail the comparison” if there is _any_ difference as there may be some expected or accepted differences. However, it would be desirable to give the user a yes/no answer. This would mean that:

1. The goal *has* to be complete parity
2. The expected differences are specifiable
3. We don't aim for a strong yes/no answer

The third option seems the most preferable.

Detailed information can be presented as an HTML report.

# What is to be compared (i.e. outcomes)

The majority of builds produce one or more file artifacts, and this is their primary purpose. The comparison functionality will revolve around comparing files. Furthermore, it is generally not practical to “see inside” a system other than Gradle. The only feasible means of communication with an “other” system is via the file system. 

It may be desirable to compare things that are not naturally files. For the case where a system other than Gradle is involved, the strategy will be to have the other system serialise a representation of the outcome of interest to the file system for comparison. For example, the details of test execution could be compared by comparing JUnit XML output or internal compile classpaths can be compared by writing the classpath out to a text file with each entry to one line.

For the case of Gradle to Gradle comparisons it may not be necessary to communicate via the filesystem. In this case, we can compare models returned by the tooling API if necessary. However, at least initially, we will only be interested in comparing things that are available via the filesystem.

## Archives

In the initial version of this functionality, only the binary contents of archives will be compared and the individual contents. 

Aspects to be compared are:

* The “relative” path to the generated archive
* Any archive metadata
* The list of entries
* The checksum of matching entries
* Size (variant on above, but a good indicative value)

### Content-wise comparison

Files can contain volatile information, that will always produce a difference when compared with a logically equivalent file. Files may contain things such as:

* Timestamps
* Environmental properties
* Relative paths

As such, it will be necessary to employ pluggable comparison strategies for different kinds of files. The strategy to use may be inferred from the file extension, but likely this will be insufficient for the majority of cases. The user must be given control of this, as it will not be possible for Gradle to automatically identify such expected differences automatically in all cases.

There are two possible strategies that can be used to deal with this fact:

1. Parsed comparison
1. Filtered comparison

A “parsed comparison” would involve effectively deserialising the file into an in memory representation that can be compared in a flexible way. A “filtered comparison” would involve transforming the file before comparison to _remove_ aspects that may be acceptably different.

For text files, a filtered comparison would be reasonably easy to facilitate by allowing the user to provide regular expression based search/replace.

There is a third option which is effectively a combination of the two. In this approach, the file is parsed and then a filtered version written back out. This could be used to deal with the fact that the Groovy compiler embeds timestamps into class files. The class file could be read with something like ASM, the timestamp field removed, and then written back to disk for conversion. The key difference is that in the end filesystem objects are compared instead of in memory objects.

# Integration test coverage

## General

The feature can be tested by verifying migrations whose outcome is known. The comparison can be run, and the HTML report inspected to understand the result.

## Upgrading Gradle Versions

It may be difficult to test “failed” comparisons for the “upgrade Gradle” case. However, given that this is just a special configuration where both source and target builds are the same, the “failed” comparison path can be tested by comparing two non equivalent builds.

## Other comparisons

Builds that are known to be equivalent can be compared. Builds that are known to be non equivalent can be compared. In short, the expected result is known and that can be verified as the actual result.

# Implementation approach

## User Interaction

The functionality is packaged as one or more plugins. The plugin, amongst other things, adds a task (e.g. `compareBuilds`) that invokes the comparison process.

There will need to be other tasks and model elements that feed into this comparison task. At the least, there will need to be two other tasks that actually invoke the builds to be compared. There may be separate tasks that generate the model of what is to be compared without actually building creating what is to be compared.

### Modification free comparison

It is desirable to support a mode of operation where the user does not need to modify their build to use this functionality. This could be achieved if Gradle can 
infer that a plugin should be applied to fullfil what it was asked to do and if the Gradle project model is sufficiently externally configurable.

For example, the user could test a Gradle upgrade by executing:

    ./gradlew compareBuild toVersion=1.3

This would implicitly apply the plugin that provides the comparison functionality and compare with Gradle 1.3.

### Plugins/Tasks/DSL

#### Comparison *task*

At the heart of this process, will be a task (named `CompareBuildOutcomes`) implementation that does the following:

* Accepts a specification of the comparison (the outcomes from both sides of the comparison, and how they are associated)
* Accepts a build outcomes comparator (that performs the comparison process and returns a result object)
* Renders the result to one or more formats (using the `Reporting` types)

This task will be very light as all it's doing is joining the elements together and invoking the comparison then rendering. It's unlikely that users will interact directly with this task other than to configure the reporting.

#### GenerateBuildOutcomes *task* *interface*

A new `Task` subinterface (named `GenerateBuildOutcomes`) will be introduced for tasks that perform some work to generate and fulfill build outcomes.

This interface will have one additional method that provides the model of the outcomes after task execution.

#### Gradle Build model *task*

This task (named `GenerateGradleBuildOutcomes` *implements* `GenerateBuildOutcomes`) will be responsible for building and fulfilling the outcomes model for Gradle builds.

The task accepts as configuration:

* The filesystem location of the to-be-built Gradle project (defaults to current project)
* The target Gradle version of the to-be-built Gradle project (default to version in use)
* The invocation to use to build the outcomes (defaults to `assemble`)

#### Gradle Comparison *domain object*

A new domain object (named `GradleBuildComparison`) will be added to serve as the specification for a Gradle to Gradle comparison. 

This object will create and configure (i.e. wire together):

* A `GenerateGradleBuildOutcomes` task for the 'from' version
* A `GenerateGradleBuildOutcomes` task for the 'to' version
* A `CompareBuildOutcomes` task

This object serves as a kind of controller, integrating the pieces of the comparison toolkit necessary for a gradle to gradle comparison.

The API of this object will largely be composed of subsets of the API of its constituent tasks, aiming to unify them in a meaningful way.

e.g.

    extensions.create("comparison", GradleBuildComparison)
    comparison {
      from.gradleVersion = "1.3"
      to.gradleVersion = "1.5"
      tasks "assemble" // configures both sides
    }

It may be sufficient to just expose the underlying tasks.

    comparison {
      from instanceof GenerateGradleBuildOutcomes
      compare instanceof CompareBuildOutcomes
    }

##### Upgrading from pre 1.2 Gradle versions

Gradle 1.2 will be the first version of Gradle that as this comparison functionality. As such, special accommodations need to be made to help people upgrading from earlier versions.

The issue is that to generate the outcomes model, a tooling API model is needed that will not be available in older Gradle versions. In this scenario, instead of
generating the outcomes we can infer them from the other side of the comparison (as we will do for Ant and Maven initially). This implies that at least one side
of the comparison must be 1.2 or higher.

There's an implementation difficulty in this in that `GenerateGradleBuildOutcomes` will need extra configuration in this mode (namely, the set of outcomes to duplicate).

#### Gradle Upgrade *plugin*

There will be a plugin tailored to running Gradle upgrade comparisons, named `verify-gradle-upgrade`.

The application of this plugin will add an instance of `GradleBuildComparison` as a build language extension (named `verifyGradleUpgrade`).

    apply plugin: 'verify-gradle-upgrade'
    
    verifyGradleUpgrade.to.gradleVersion "1.4"

This object would also likely expose the reporting options from the comparison task.

    verifyGradleUpgrade.reporting {
      html.destination = file("blah.html")
    }

The actual comparison task (that the user invokes) that this plugin adds would be called `verifyUpgrade`.

#### Outcomes inference *task*

There will be a task (`GenerateInferredBuildOutcomes` *implements* `GenerateGradleBuildOutcomes`) that executes a process and builds an outcomes model by inferring it 
from another model.

This task will accept as configuration:

* A “base” directory
* A build outcomes model (to infer from)
* An exec spec (or, the task exposes an exec spec for configuration)

In this case, the system that we are generating outcomes for is opaque in terms of modelling the outcomes. After executing the process, the set of outcomes is inferred based on the input outcomes model. That is, a new outcome is generated for each outcome in the input outcomes model adjusted to be related to *this* build (e.g. adjusting relative paths to the given base dir).

#### Gradle Migration Comparison *domain object* 

Similar to `GradleBuildComparison`, there will be a domain object (named `GradleMigrationComparison`) that performs a similar function except that the *to*
side is based around a `GenerateInferredBuildOutcomes` task.

#### Gradle migration *plugin*

There will be a plugin tailored to running migration comparisons, named `verify-gradle-migration`. This plugin is tailored to configuring a comparison between a 
non-Gradle *from* system and a Gradle build on the *to* side. This plugin will (at least initially) be what users migrating from Ant or Maven will use.

The plugin will add an instance of `GradleMigrationComparison` as a language extension.

e.g.

    apply plugin: 'verify-gradle-migration'
    
    verifyGradleMigration {
      from.exec {
        executable "ant"
        args "build"
      }
    }

The actual comparison task (that the user will invoke) would be named `verifyMigration`. 

##### From inference

It may be possible to infer sensible defaults for the from build exec. For example, if a `build.xml` is found we can preconfigure for Ant and similarly for a `pom.xml`
file and Maven.

As we will be inferring the “from” model from the Gradle model (“to” side), this implies that the “to” side is as at least Gradle 1.2 (as earlier versions) do
not have the necessary Tooling API models.

#### “Manual mode” & Extensibility

There will be a public set of tools for constructing the “from” side of the comparison, and the assocations manually for complicated cases. The toolkit will consist
of: 

* A way of describing the *file based* outcomes of another system
* A a way of associating the outcomes to Gradle outcomes on the “to” side
* A way to specify content wise comparisons 

The plugins and tasks listed above will essentially be a preconfigured arrangement of these lower level pieces. In the initial implementation, providing extensibility is not a design goal. For example, using one of the plugins above effective hard codes the types of outcomes that can be compared and rendered. 

The underlying lower level pieces will facilitate this kind of extensibility. The plugins and language extensions will not provide “hooks” into these extensibility aspects. For example, it will not be possible to “register” a new kind of outcome to be compared when using the plugins. This may change over time.

It will be possible to use the functionality in a kind of manual mode. That is, you get no out of the box wiring from a Gradle plugin but can assemble and extend
the lower level pieces to suit your needs. Over time we may add sugary builders or something similar as language extensions to make this kind of manual mode easier but this is not an immediate priority.

# Examples of use of this functionality

The following section describes how this functionality will be ideally integrated by the user into a migration or upgrade.

## Gradle upgrade

1. User identifies that there is a new Gradle version available
1. User goes to to-upgrade Gradle project and executes: `gradle tryUpgrade [newGradleVersion=«latest»] [args=assemble]` (elements in [] are optional) 
1. If there are no differences (i.e. the outputs are byte-for-byte equivalent) the user is told so, otherwise they are pointed to the created HTML report of the differences
1. User inspects the differences and determines whether they are benign or not.
1. If benign, the user updates the wrapper properties to use the new Gradle version*

* - In the future there may be a CLI shortcut for updating the wrapper. This would mean the user could do:

1. `gradle tryUpgrade`
1. inspect report
1. `gradle upgradeWrapper`

# Open issues

## General

* To generate a model of the Gradle build outputs: how do we determine which tasks produce outputs, run those tasks, then
inspect the build model for the configuration of where the archives are?

* Does verification always include execution of the "old" and "new" builds, or can it work off preexisting outputs?

* How much knowledge does verification need about the "old" and "new" builds? Is it good enough to have two
sets of outputs (together with a mapping between them), or is additional information required?

* How much information does the feature need about how and by whom an artifact that is to be compared was produced?
Does it need to be able to compare different representations of the same information, like a Gradle JUnit report and
an Ant JUnit report (assuming the report formats are different), or a Gradle JUnit report produced by Gradle version X
and one produced by Gradle version Y?
