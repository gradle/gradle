Task inferred invocation time plugin application refers to having a plugin applied *at build invocation time* in response to tasks that were requested to be executed, which means that it is not applied because of the content of build script. That is, the plugin can be applied by the build user as opposed to the build author.

The specification of only applying at *build invocation time* is important. Traditional task rules are applicable at any time during the build lifecycle. We are only considering invocation time for reasons of implementation simplicity and understandability (for users).

## Relationship with invocation time model configurability

This feature becomes much more compelling when a certain amount of configurability is possible at invocation time, which is a separate feature with a [separate specification](https://github.com/gradle/gradle/blob/master/design-docs/task-configuration-from-command-line.md).

This spec will assume this capability exists (or will exist) in so far as it is possible to influence the configuration of tasks that are added via plugins applied at invocation time.

## Use cases

### “Global” tasks

There are certain tasks that are generally applicable to all builds. At the moment, such tasks are part of the implicit task container. Examples of are tasks such as `dependencies`, `projects`, `tasks` etc. The defining characteristic of a global task is that it is useful on all (or most) builds with little or no configuration.

The identified problems with the implicit tasks approach are:

1. It's a separate space/concept (i.e. adds complexity)
2. It's not extensible (users cannot contribute to this space)
3. Implicit tasks are not user configurable (because they live in a different task container)

With this feature, the implicit task container would be removed. Tasks such as `dependencies`, `projects` etc. would be moved to a plugin that can be applied at invocation time.

### Initialisation tasks

These are tasks that you would want to use to create a Gradle project. This could be used for something similar to Maven archetype support.

In an empty directory, something like…

    ./gradlew initJavaProject

Where the `initJavaProject` task is supplied by a plugin that is somehow applied at invocation time.

There may be many different kinds of init type tasks; for different types of templates.

### Temporary tasks

Tasks such as `wrapper` and the “build comparison for Gradle upgrades” task are only needed for a short amount of time. It would be more convenient for the user if they could use this functionality without needing to modify the build script. 

### Environmental/personal tasks

Tasks such as the `idea` and `eclipse` tasks could be invoked without having to add the associated plugins to the build script. This would allow you to checkout a project, and generate the IDEA metadata for it without touching it. For example, they may have checked the project out just to browse the source in their IDE of choice and shouldn't be forced to edit the build script to achieve this.

Some more use cases:

* tasks that manage the build environment (eg enable/disable the daemon)
* tasks that manage and verify credentials
* tasks that set up a developer environment for a given project (eg find and check it out, install the appropriate tools and services)
* tasks that manage build aggregation
* tasks that manage the daemon (eg show status, stop all, etc).

## Implementation ideas

### 1. Use existing plugin metadata file

We already have a metadata file for binary plugins that maps the common name to the implementing class. This could be extended to include tasks that the plugin can satisfy…

    # META-INF/gradle-plugins/idea.properties
    implementation-class=org.gradle.plugins.ide.idea.IdeaPlugin
    implicit-tasks=idea,cleanIdea,ideaWorkspace

**Pros:**

1. Simple for implementors, does not introduce new files or constructs.

**Cons:**

1. Potentially expensive to dereference. Would involve reading every plugin descriptor available until one is found that provides the task.

### 2. Using a metadata file per implicit task

Introducing a convention such as…

    # META-INF/gradle-implicit-tasks/cleanIdea.properties
    implementation-class=org.gradle.plugins.ide.idea.IdeaPlugin

**Pros:**

1. Efficient to dereference, the classpath can be scanned for `META-INF/gradle-implicit-tasks/«task name».properties

**Cons:**

1. A file per implicit task (clumsy for implementors, could be minimised by a “Gradle Plugin” plugin that generates these files from annotations etc.)
2. Duplication between this task and the plugin implementation, creating risk of divergence (i.e. the plugin changing to no longer provide the advertised task)
