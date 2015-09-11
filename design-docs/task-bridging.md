This spec improves the usability of rule based model configuration, specifically by improving “bridging” to the legacy configuration space.

The term bridging refers to making model elements visible in both the new formal model graph and the legacy Project space.
That is, tasks created via rules shall be “accessible” and tasks created outside of rules are “accessible” to rules.
This spec gradually defines exactly what “accessible” means in this context.

While the patterns here may be applicable to other types of bridged things, this spec focuses on tasks.

# Stories

## User configures a non-rule based task to depend on, by type, rule based tasks.
A build author should be able to reliably depend on rule based tasks by using the `tasks.withType(..)` construct.

e.g.

```groovy
class Rules extends RuleSource {
    @Mutate
    void addTasks(ModelMap<Task> tasks) {
        tasks.create("climbTask", ClimbTask) {}
    }
}
apply type: Rules

task customTask << { }
customTask.dependsOn tasks.withType(ClimbTask)
```

### Implementation
Introduce a `RealizableTaskCollection` which `org.gradle.api.internal.tasks.DefaultTaskDependency` uses to trigger realisation of tasks by type.
Because the `.withType()` construct can be used _after_ the project has been evaluated e.g. `taskGraph.whenReady{ project(":a").tasks.withType(Foo) }`
any use of `withType()`, post project evaluation, will result in tasks of that type being realised immediately.

Realise in this context means realising the model nodes of tasks of those types along with all child nodes.

_Implementation Spike_

~~1. Using a `ProjectConfigureAction` and tracking all task types addressed via `withType()`
    - Add a new `ProjectConfigureAction` to the `ProjectEvaluator` which would act as the trigger point to realise all rule based tasks which have been addressed via `.withType()`.
    This would be added via [BuildScopeServices](http://github.com/gradle/gradle/blob/master/subprojects/core/src/main/groovy/org/gradle/internal/service/scopes/BuildScopeServices.java#L173-173)
    - Override the `public <S extends Task> TaskCollection<S> withType(Class<S> type)` method in [DefaultTaskContainer](http://github.com/gradle/gradle/blob/master/subprojects/core/src/main/groovy/org/gradle/api/internal/tasks/DefaultTaskContainer.java).
    This implementation would add each supplied `Class<S> type` to a `Set`. This `Set` would then be used by a `ProjectConfigureAction` to realise all of the necessary tasks.~~

~~2. Post evaluation use of `.withType()` triggers task realization immediately but only for task types which have not already been realised.~~


### Test cases

- Happy path as per the example above.
- A non rule source task can depend on multiple rule source tasks of type `ClimbTask`
- A non rule source task can depend on one or more tasks of type `ClimbTask` created via both rule sources and the traditional task container.
- Using various ways of addressing the task which depends on tasks of type `ClimbTask`
    - `tasks.customTask.dependsOn tasks.withType(ClimbTask)`
    - `project.tasks.customTask.dependsOn tasks.withType(ClimbTask)`
    - `tasks.getByPath(":customTask").dependsOn tasks.withType(ClimbTask)`
- Only rule source tasks of type `ClimbTask` are realised given rule source tasks of other types exist.
- Depending on rule based tasks of a type in another project
- depending on tasks of a type from a project that is already fully evaluated

e.g.

```groovy
task foo  {
    dependsOn project(“:bar”).tasks.withType(RulesTask)
}
```
- Build failure when failing to create a rule based task.
- Realizing all subtypes of a rule source task - `customTask` should have a dependency on any tasks of type `Child`
e.g.

```groovy
class Child extends Parent {}
class Parent extends DefaultTask {}
customTask.dependsOn tasks.withType(Parent)
```

### Open Questions:
- ~~Should we reach across projects i.e. `project(":projectA").tasks['customTask'].dependsOn tasks.withType(ClimbTask)` where `ClimbTask` is a rule
 source task added by 'projectB'~~ Yes

## User configures rule based task in build script directly

Tasks created via rules should be retrievable from the task container by name/path in the same manner that non rules tasks are.

At the point the task is asked for, the rule creating the task must be known, as the actual task instance will be returned.
That is, nothing like a recording proxy or any other kind of facade is returned.

```groovy
class Rules extends RuleSource {
    @Mutate
    void addTasks(ModelMap<Task> tasks) {
        tasks.create("climbTask", ClimbTask) {}
    }
}

apply type: Rules
assert tasks.climbTask.steps == 0
```

### Implementation

TBD

### Test coverage

- Happy path: task can be created and configured
- Rule based task is requested, but an error occurs while executing rules to create it
- Rule based task is requested before rule that creates it is discovered
- Mutation rule is added for task after the task has been accessed directly
- Mutation rule is added for the task container after the task has been accessed directly
- Rule based task is mutated via direct access after it has been used as an input to another rule (i.e. it is fully realised)
- `tasks.withType(Foo).someFooTask` should return the task

### Open questions

- What lifecycle state is the task in when it is returned (e.g. is it fully realised?)
    - What happens if new rules are discovered after the task is accessed directly?
        - Rules for the task?
        - Rules for the task container?
- What happens if the task is mutated outside of rules when it's already been used an input?

## User executes action against rule based task, via tasks.all(), that is not part of task execution graph

This story allows conventional usage of with `tasks.all(Action)` construct to apply to rule based tasks.
Specifically, to tasks that are not going to be created due to their inclusion in the task execution graph.

```groovy
class Rules extends RuleSource {
  @Mutate
  void addTasks(ModelMap<Task> tasks) {
    tasks.create("foo", Foo)
  }
}

apply type: Rules

def taskNames = []
tasks.all {
  taskNames << it.name
}

task test {
  doFirst {
    assert taskNames.contains("foo")
  }
}
```

### Test Coverage

- Use of tasks.all() forces creation of _all_ tasks
    - task placeholders
    - tasks added via `ModelMap<Task>`
- Rules creating tasks or mutating task containers can be discovered after `tasks.all`
- Tasks created by rules discovered after `tasks.all` are created and the action invoked for them

### Open issues

- Exactly when will the rule based tasks be created?
- Did .all() “find” placeholder tasks before they were rule based? If so, they must be included, otherwise not.

## User executes action against rule based task, by type via task container, that is not part of task execution graph

This story improves on the previous by only forcing the creation of tasks with the matching type.

```groovy
class Rules extends RuleSource {
  @Mutate
  void addTasks(ModelMap<Task> tasks) {
    tasks.create("foo", Foo)
  }
}

apply type: Rules

def taskNames = []
tasks.withType(Foo) {
  taskNames << it.name
}

assert taskNames == ["foo"]
```

### Test coverage

- Rule based tasks not matching type are not created
- `withType(Foo).all()` does not force all tasks to be created, just the `Foo` tasks

## User executes action against rule based task, via tasks.matching(), that is not part of task execution graph

Should behave similarly to .all()

# Open issues

> Given a task of type `Foo` created by a known rule, should `tasks.withType(Foo).toList()` contain the task?

Builds are using this pattern, despite it not being best practice.
It generally works as the majority of tasks are created by plugins that are typically applied early.
That is, the tasks actually exist.
However, iterating tasks during configuration is almost always a bad idea as it does not include tasks yet to be created.

An alternative to eagerly creating the task would be to defer its creation until all rules have been discovered, but before configuration has “completed”.
This would support constructs such as:

```
task install(dependsOn:  subprojects.collect { Project p -> p.tasks.withType(PublishToMavenLocal) })
```

As this collection is not actually iterated until the task graph is being constructed, creating just before that time would suffice.

# Backlog

- Better handle `tasks.<name>` in imperative API/DSL. Should attempt to discover task rules before failing, currently does not, only attempts to realise the task node.
- Apply before-each and after-each rules to tasks defined using imperative DSL.
- Apply `tasks.all { }` actions between initializer and mutation rules.
- Better handle case where `check`, `build`, etc tasks are define using rules, either by allowing this and emitting deprecation warning, as when done using legacy API,
  or improved error message on conflict.
- RealizableTaskCollection should work with collection semantics (i.e.`customTask.dependsOn tasks.withType(ClimbTask) + tasks.withType(JumpTask)`)
