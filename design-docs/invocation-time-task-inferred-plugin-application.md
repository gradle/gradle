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

## Implementation ideas

### 1. Use existing plugin metadata file

We already have a metadata file for binary plugins that maps the common name to the implementing class. This could be extended to include tasks that the plugin can satisfy…

    # META-INF/gradle-plugins/idea.properties
    implementation-class=org.gradle.plugins.ide.idea.IdeaPlugin
    implicit-tasks=idea,cleanIdea,ideaWorkspace

**Pros:**

1. Simple for implementors, does not introduce new files or constructs.
2. Provides meta-data that can be used to include in the DSL reference the list of tasks provided by a task, and an index of all known tasks.
3. Provides meta-data that can be used to provide informative error messages, for example: Task 'cleanIdea' not found. Did you mean to apply the 'idea' plugin?
4. Provides meta-data that can be used to implement content assistance in the IDE. So, given apply plugin: 'idea', we know that 'cleanIdea' and 'tasks.cleanIdea' is available.

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
