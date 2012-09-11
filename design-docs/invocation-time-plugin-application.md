Invocation time plugin application refers to having a plugin applied *at build invocation time*, which means that it is not applied because of the build script. That is, the plugin can be applied by the build user as opposed to the build author.

The specification of only applying at *build invocation time* is important. Traditional task rules are applicable at any time during the build lifecycle. We are only considering invocation time for reasons of implementation simplicity and understandability (for users).

Note: the original name for this feature was “auto applying plugins”. This spec as it stands does not presume to _auto_ apply plugins, though that is one implementation possibility. The fundamental problem is being able to “inject” functionality into the build from outside it which could be implemented in different ways.

## Relationship with invocation time model configurability

This feature becomes much more compelling when a certain amount of configurability is possible at invocation time, which is a separate feature with a [separate specification](https://github.com/gradle/gradle/blob/master/design-docs/task-configuration-from-command-line.md).

This spec will assume this capability exists in so far as it is possible to influence the configuration of tasks that are added via plugins applied at invocation time.

## Use cases

## “Global” tasks

There are certain tasks that are generally applicable to all builds. At the moment, such tasks are part of the implicit task container. Examples of are tasks such as `dependencies`, `projects`, `tasks` etc. The defining characteristic of a global task is that it is useful on all (or most) builds with little or no configuration.

The identified problems with the implicit tasks approach are:

1. It's a separate space/concept (i.e. adds complexity)
2. It's not extensible (users cannot contribute to this space)
3. Implicit tasks are not user configurable (because they live in a different task container)

With this feature, the implicit task container would be removed. Tasks such as `dependencies`, `projects` etc. would be moved to a plugin that can be applied at invocation time.

## Initialisation tasks

These are tasks that you would want to use to create a Gradle project. This could be used for something similar to Maven archetype support.

In an empty directory, something like…

    ./gradlew initJavaProject

Where the `initJavaProject` task is supplied by a plugin that is somehow applied at invocation time.

There may be many different kinds of init type tasks; for different types of templates.

## Temporary tasks

Tasks such as `wrapper` and the “build comparison for Gradle upgrades” task are only needed for a short amount of time. It would be more convenient for the user if they could use this functionality without needing to modify the build script. 

## Environmental/personal tasks

Tasks such as the `idea` and `eclipse` tasks could be invoked without having to add the associated plugins to the build script. This would allow you to checkout a project, and generate the IDEA metadata for it without touching it. For example, they may have checked the project out just to browse the source in their IDE of choice and shouldn't be forced to edit the build script to achieve this.

## Implementation ideas

### 1. Inference from task name

This class of ideas refers to infering the plugin to apply, from the name of a task that was specified to be executed at invocation time.

That is, given:

    ./gradlew cleanIdea

It will be inferred that the 'idea' plugin needs to be applied to satisfy the request.

**Pros:**

1. Extremely convenient for users. They specify what they want, and Gradle complies. 
2. Minimal typing
3. Decouples functionality from providing plugin (not entirely a pro)

**Cons:**

1. It's a flat namespace so there's an increased risk on name collision and it would be impossible to have variants of the task (implemented by different plugins)
2. The plugin to be applied has to provide a task that is to be executed, it can't just decorate or observe (e.g. for `--profile` functionality)
3. It's a new mechanism/complication that makes the system as a whole less predictable
4. Has discoverability implications, i.e. `gradle tasks` no longer gives you a complete picture of what you can do with the build
5. Decouples functionality from providing plugin, potentially making it harder to find the documentation

#### 1.1. Use existing plugin metadata file

We already have a metadata file for binary plugins that maps the common name to the implementing class. This could be extended to include tasks that the plugin can satisfy…

    # META-INF/gradle-plugins/idea.properties
    implementation-class=org.gradle.plugins.ide.idea.IdeaPlugin
    implicit-tasks=idea,cleanIdea,ideaWorkspace

**Pros:**

1. Simple for implementors, does not introduce new files or constructs.

**Cons:**

1. Potentially expensive to dereference. Would involve reading every plugin descriptor available until one is found that provides the task.

#### 1.2. Using a metadata file per implicit task

Introducing a convention such as…

    # META-INF/gradle-implicit-tasks/cleanIdea.properties
    implementation-class=org.gradle.plugins.ide.idea.IdeaPlugin

**Pros:**

1. Efficient to dereference, the classpath can be scanned for `META-INF/gradle-implicit-tasks/«task name».properties

**Cons:**

1. A file per implicit task (clumsy for implementors, could be minimised by a “Gradle Plugin” plugin that generates these files from annotations etc.)
2. Duplication between this task and the plugin implementation, creating risk of divergence (i.e. the plugin changing to no longer provide the advertised task)

### 2. Explicit application

Instead of trying to infer or “auto” apply plugins, we could simply provide a way to explicitly apply plugins at invocation time. Like an apply() syntax for the CLI. 

    ./gradlew -Aidea cleanIdea
    
Where `-Aidea` is the equivalent of having `apply plugin: 'idea'` at the very start of the build script.

**Pros:**

1. Is applicable to any plugin/task without any new metadata
2. Simple extension of existing functionality/mechanism
3. Supports plugins that are observers/decorators (`gradle -Aprofile build`)
4. Supports namespacing of a sort (`gradle -Aupgrade-comparison compareBuilds` && `gradle -Amigration-comparison compareBuilds`)

**Cons:**

1. Reduced user convenience (`gradle -Aidea cleanIdea` vs. `gradle cleanIdea`)
2. User has to know the short name of the plugin that supplies the functionality