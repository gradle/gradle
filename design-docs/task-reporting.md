## Story: Improved performance of tasks report

Large multi-project builds with a lot of tasks suffer from poor performance when executing the `tasks` task. It's not unusual to see a task execution
 time of multiple minutes for enterprise projects which renders the report unusable. The goal of this story is to improve the performance of the tasks 
 report by optimizing its internal workings and the information rendered for the user.

### User visible changes

* The task report will only render so-called public tasks by default. Public tasks are tasks that set a non-null value for the [group property](https://docs.gradle.org/current/dsl/org.gradle.api.Task.html#org.gradle.api.Task:group).

<!-- -->

Given the following `build.gradle`:

    task a {
        group = 'Hello world'
    }

    task b

Output of `gradle tasks` **before** the change:

    Hello world tasks
    -----------------
    a

    Other tasks
    -----------
    b

Output of `gradle tasks` **after** the change:

    Hello world tasks
    -----------------
    a

* Tasks with a null value for the `group` property can be rendered by using the command line option `--all`.

<!-- -->

Given the following `build.gradle`:

    task a {
        group = 'Hello world'
    }

    task b

Output of `gradle tasks` or `gradle tasks --all` **before** the change:

    Hello world tasks
    -----------------
    a

    Other tasks
    -----------
    b

Output of `gradle tasks --all` **after** the change:

    Hello world tasks
    -----------------
    a

    Other tasks
    -----------
    b

* Tasks with dependencies but a null value for the `group` property used to only render the top-level task (typical an end user entry point) and omit rendering the task dependencies. 
With this change task dependencies will not be folded anymore.

<!-- -->

Given the following `build.gradle`:

    task a

    task b {
        dependsOn a
    }

Output of `gradle tasks` or `gradle tasks --all` **before** the change:

    Other tasks
    -----------
    b

Output of `gradle tasks --all` **before** the change:

    Other tasks
    -----------
    b
        a

Output of `gradle tasks --all` **after** the change:

    Other tasks
    -----------
    a
    b

* Tasks with dependencies and a non-null value for the `group` property used to render their dependencies in square brackets when using the command line option `--all`. 
With this change task dependencies will not be rendered anymore.

<!-- -->

Given the following `build.gradle`:

    task a {
        group = 'Hello world'
    }
    
    task b {
        group = 'Hello world'
        dependsOn a
    }

Output of `gradle tasks --all` **before** the change:

    Hello world tasks
    -----------------
    a
    b [a]

Output of `gradle tasks --all` **after** the change:

    Hello world tasks
    -----------------
    a
    b

### Implementation

* Unchanged behavior:
    * The end user will still invoke the `tasks` task to render available tasks for the project.
    * No changes are needed for the parsing of the command line parameter `--all` available to the `tasks` task.
    * Default tasks (e.g. like `dependencies` or `help`) will always be rendered in the task report.
    * In a multi-project build tasks of sub projects will still be taken into consideration.
    * Task rules will always be rendered in the task report.
* Change the implementation `TaskReportTask` as follows:
    * Avoid determining and rendering private tasks when the `detail` property is set to `false`.
    * Take into account private tasks when the `detail` property is set to `true`.
    * Avoid walking a task's dependencies to determine if it is considered an entry point.
    * Avoid determine a task's dependencies. The report does not reflect task dependencies anymore.
* Update documentation in user guide to reflect the changes for the end user. Clearly establish the notation of "task visibility" (public vs. private).
* Update training slides and labs to reflect the changes for the end user.
* Out of scope for this story is a new report that renders the full task graph similar to the `dependencies` report.

### Test coverage

* Change or extend existing tests to only render public tasks when `tasks` is executed without `--all`. 
* Change or extend existing tests to render public and private tasks when `tasks` is executed with `--all`.
* Verify that the same behavior is observed for tests defining tasks via the software model. 
* Fix existing test coverage in other module that might failed based on the changed behavior.
* Set up or reuse an existing performance test for the purpose of demonstrating the performance improvements to `gradle tasks`.

### Open issues

* To a certain extent the new behavior is a breaking change. Are we concerned about making the change in a minor version of Gradle?
* Does this change have any effect on the Tooling API and tools consuming it e.g. Buildship? As far as I know [Buildship by default only offers public tasks](https://discuss.gradle.org/t/buildship-eclipse-custom-tasks-in-the-gradle-tasks-view/12172/6) in the _Gradle Tasks_ view.
* Does this change have any effect on the build receipt created by GCS?

## Story: Introduce report that renders the full task graph of a project

TBD
