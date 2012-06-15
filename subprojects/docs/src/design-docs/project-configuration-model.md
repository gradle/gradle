# What is this?

A reworking of the Gradle configuration model, to allow many new features.

# Why?

#### Reduce build time for developers that use a build with a large number of projects

To do this, we build the model for only the projects that we need to do the requested work, rather than build the model for all the projects in the build

For example, for a very large build, rather than instantiating and configuring all 800+ projects and associated tasks, configurations and so on, just
instantiate and configure the 10 projects that the developer happens to be working on. This improves performance in 2 ways, by avoiding unnecessary work, and by avoiding generating unnecessary garbage on dev machines with limited available memory.

#### Allow parallel and distributed execution

To do this, we disallow the build logic for one project from (directly) reaching the domain objects of another project. Gradle can then instantiate,
configure and execute projects in separate processes, and/or with a separate thread for each project. This decoupling means that projects can be configured in parallel, tasks from different projects can execute in parallel, and that task execution and project configuration can happen in parallel.

#### Reduce incremental CI build time for a large project

To do this, we allow a project to declare its inputs and outputs, including those of its configuration code. We short-circuit both building the model
for the project, and executing the tasks of the project, when a given project model and the requested output is up-to-date wrt to their inputs.

#### Allow a developer to work on multiple components at once

For example, to make changes in two related components on a local machine, or to build a local copy of a dependency, or to verify that local changes
work with dependent components before committing changes. These use cases are also relevant for CI builds too.

To do this, we use global identifiers for the project publications we depend on, so that there is no difference in the DSL between a project and an
external module dependency. Gradle can then choose at runtime whether a publication should be resolved from a repository, or built locally.

#### Allow a developer to work on a small number of projects in the IDE

To do this, we make use of the changes for 2. and 4. above. Given that the build logic for a project cannot assume that any other project models are
available, and that Gradle is free to choose a project or external dependency at runtime, the IDE integration can provide a focussed view of the build.

#### Allow flexible promotion schemes

To do this, we make use of the changes for 2. and 4. above. Gradle can choose to use binaries for a dependency, or to build it from source. And it can
choose to use head/latest or some label/version/revision for each dependency separately. Same for verifying dependent projects.

#### Build multiple variants across several machines

#### Others

There's plenty of other goodness that builds on the above:

* Solve some of the configuration ordering problems, by making projects independently configurable.
* Allow 'distributed' incremental build by swapping in artefacts built on other machines rather than building them locally.
* Faster tooling API model building, by making the project model cacheable.
* Fast command-line completion, by making the project model cacheable.
* Pre-build the project models in the daemon, by declaring the project model configuration inputs and outputs.
* Allow project publications to be used at configuration time, by making the projects independently configurable. The buildSrc project simple becomes
  a regular project, whose runtime publication is used as input to the configuration of each of the other projects.

# Basic model

The configuration code for a project, including applied scripts and plugins, cannot refer to domain objects or state outside that project:

* Cannot declare dependencies on tasks outside the project.
* Cannot use rootProject, parentProject, allprojects, subprojects, or project by path accessors.
* Cannot use any properties or methods of the Gradle object, that refer to projects: rootProject, beforeEvaluated { }, etc.
* Cannot use evaluationDependsOn(), evaluationDependsOnChildren(), etc.
* Cannot use inherited properties and methods.
* Cannot use inherited script class path.
* Cannot use project dependencies.
* Cannot use TaskDependency.getDependencies(Task).

In addition, configuration code cannot make assumptions about the ordering of project configuration:

* Cannot resolve a project dependency in the buildscript { } section of a script.
* Cannot resolve a project dependency during the execution of a script, except for those declared in the buildscript { } section.

A project can declare the set of publications it can produce as output.

* A publication is a named, strongly-typed set of artefacts and meta-data.
* An artefact is a binary resource.
* Meta-data has a corresponding artefact
* Each artefact is built by one or more tasks in the project.
* Each publication has a corresponding set of publications it requires as input.

Given this, when Gradle is invoked, it will be possible to dynamically choose the set of projects to be configured for that Gradle invocation. Let's
call these the 'active' projects.

* Each active project has its own Project instance. No domain objects from outside the project will be reachable via this Project instance.
* Task instances will only be created and configured for active projects.
* Only the task instances
* Only tasks from active projects will be executed during a build.
* Only events from active projects will be fired during a build.

# Open issues

* Projects need some kind of global identifier, if they are to be decoupled from a build:
    * For use in project dependencies.
    * For artifact and module identifiers.
    * For use in task identifiers.
    * For logging and error reporting.
    * For visualisation in, for example, in discovery.

* Does it still make sense to have a project hierarchy (other than for backwards-compatibility)?

* Some configuration use cases we need to deal with:
    * `allprojects { ... }` in a root build script.
    * `project(':someProject') { ... }` in a root build script (i.e. all config in one script).
    * aggregation tasks, eg aggregated javadoc, or test reports that drag in source and configuration from some other projects.
    * an init script uses `allprojects { }` or `rootproject { }`
    * conditional configuration based on task graph contents.
    * IDE and sonar plugins build model from subprojects.
    * project configured using previously published version of itself.
    * there are more use cases here.

* Need some way for a script/plugin to opt-out of the configuration rules above, in some way that does not couple all of the projects together.

* What should we do with global events? e.g. ProjectEvaluationListener.beforeEvaluate(). Sometimes, the action works only on the event parameters and
  could be serialised into the target jvm. Sometimes, the action collects up some global state which needs to end up in a single jvm.

* What should we do with the task graph? Currently, TaskExecutionGraph provides access to tasks from all projects. We cannot continue to do this.

* Invoking Gradle with unqualified task names. This is going to require every project that lives under the current directory to be configured. Same
  for -x.

* Should be possible to specify a publication to build, instead of a task to execute, from the command-line.

* How do we define the set of active projects for a build?
    * Do we keep the existing separation, where one script (settings.gradle) defines a set of projects and where to find them, and another script
      (build.gradle) defines how the particular project should be configured?
    * Or, do we add a new script type, (with some new kind of delegate) that can declare projects, their locations, and their configuration?
    * Must be able to compose sets of projects into larger aggregates.
    * Must be able to partition a large set of project into a smaller subset of projects.

* How do we define where to find the outputs of non-active projects?

* Dealing with plugins that have a cross-project model or state, such as the IDEA project, or the sonar multi-project support.

* Dealing with conflicting build environment specifications.

* Dealing with 'gradle buildDependents'.

* The DSL should be structured in such a way that we can build the model lazily:
    1. Build the set of candidate projects that might be referenced during the build. We only need either (project-identifier, project-dir,
       configure-script) or (project-identifier, publication-repository) at this point.
    2. Determine the initial set of active projects, based on project-dir and the set of task names provided.
    3. For each active project, determine the required set of inputs. Ideally, this would be done without instantiating and configuring all the tasks
       of the project. We only need (project-identifier, publication-name) at this point.
    4. Map each input to either a project dependency or external dependency. For project dependencies, add the publishing project to the set of active
       project.
    5. For each active project, instantiate and configure the set of tasks that will be executed for the project (if we managed to do 3. without doing
       this). Ideally, this would instantiate and configure only those tasks that are needed to build the required outputs.
    6. Repeat 3, 4 and 5 until there are no active projects which have not been configured. This could become parallel and distributed at some point.

* Have some way to stop at step 3. above for a given project, if we can determine that its outputs are up-to-date.
