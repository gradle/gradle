Task inferred invocation time plugin application refers to having a plugin applied *at build invocation time* in response to tasks that were requested to be executed, which means that it is not applied because of the content of build script. That is, the plugin can be applied by the build user as opposed to the build author.

The specification of only applying at *build invocation time* is important. Traditional task rules are applicable at any time during the build lifecycle. We are only considering invocation time for reasons of implementation simplicity and understandability (for users).

## Relationship with invocation time model configurability

This feature becomes much more compelling when a certain amount of configurability is possible at invocation time, which is a separate feature with a [separate specification](https://github.com/gradle/gradle/blob/master/design-docs/task-configuration-from-command-line.md).

This spec will assume this capability exists (or will exist) in so far as it is possible to influence the configuration of tasks that are added via plugins applied at invocation time.

## Use cases

* tasks that provide insight on any & all projects (e.g. `dependencies`, `projects`, `tasks`)
* tasks that create a skeletal project (something like Maven archetypes)
* tasks that manage the build environment (eg enable/disable the daemon)
* tasks for managing the wrapper
* tasks that manage and verify credentials
* tasks that set up a developer environment for a given project (eg find and check it out, install the appropriate tools and services)
* tasks that manage build aggregation
* tasks that manage the daemon (eg show status, stop all, etc).
* tasks that make IDE integration possible for other people's projects (i.e. generated IDE metadata without needing to add IDE plugin to project explicitly)

## Implementation plan

The lookup mechanism will be based on finding a file @ `META-INF/gradle-tasks/«task name».properties` on the classpath. This file is required to only have one entry: `plugin-implementation-class=«fully qualified plugin class name»`. If there is such a file for a task that a plugin creates, it is said to be “advertised”.

As an example, the to make the `clean` an advertised task the following file would need to be added:

    // META-INF/gradle-tasks/clean.properties
    plugin-implementation-class=org.gradle.api.plugins.BasePlugin

The first matching file on the classpath will be used; any others will be completely ignored.

The order of how a task name can be satisfied will be:

1. A task with the exact given name in the defined project
2. An advertised task with the exact given name
3. Partial name matching of an existing task (e.g. camel case execution)
4. Task rules

The implication of this ordering is that partial task names cannot be used for advertised tasks (unless the plugin has not been explicitly applied by the user in the build script, making it a “regular” task). 

Also, tasks that are available via task rules are not advertisable.

The logic to look for plugins to apply to satisfy the task request will be needed to `TaskSelector`, or something similar.

## Test Coverage

* Verify the resolution order above
* Advertised task with plugin-implementation-class that is not found
* Advertised task with malformed descriptor
* Executing advertised task that is available due to explicitly applying plugin in build script

## Open Issues

It's a bit cumbersome for users to maintain these `/gradle-tasks/*` properties files. They could easily diverge from the actual plugin implementation. We could generate these static properties files from annotations on the plugin class, as a compromise:

    @AdvertisedTasks([
        @AdvertisedTask("someTask"),
        @AdvertisedTask("someOtherTask")
    ])
    class MyPlugin implements Plugin<Project> {}

The Gradle `plugin-development` plugin that is used to build Gradle plugins could generate the static descriptor files from the annotations. The annotations are convenient for users to maintain, and the static files are easy for tooling (Gradle and IDEs etc.) to find and understand.    