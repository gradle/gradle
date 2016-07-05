# Build dependent components

This feature allows a developer to trigger the assembly and check of given components and all the components that depend on them.


## Stories

### (Story) Assemble dependents of a binary

Each binary should have a `assembleDependents` task associated.
This task should assemble both the binary itself and all its dependent components.

### Test coverage

- [ ] Assemble dependents in a single project build
- [ ] Assemble dependents in a multi project build
- [ ] Assemble dependents test suites
- [ ] Assemble dependents skip non-buildable components

### Implementation notes

- Create `assembleDependents` tasks on all binaries
- `assembleDependents` depends on its associated binary `build` task
- `assembleDependents` depends on its direct dependents `build` tasks


### (Story) Assemble dependents of a component

Each component should have an `assembleDependents`  task associated.
This task should assemble all the component's binaries and all their dependent components.

### Test coverage

- [ ] Assemble dependents of all buildable binaries of the targeted component

### Implementation notes

- Component's `assembleDependents` task depends on all the component's binaries `assembleDependents` tasks.


## (Story) Checkable components

The `LifecycleBasePlugin` registers project-wide lifecycle tasks named `clean`, `assemble`, `check` and `build`. The latter, that is `build`, depends on both `assemble` and `check`.

Software model buildable components register their `build` task as a dependency of the lifecycle `assemble` task. 
There's a terminology mismatch here, it should be *assemblable* components having an `assemble` task.

Software model test suites have a `run` task to actually run the tests. It is bound to the lifecycle `check` task.

If a build author wants to add some *check* tasks, let's say some *lint* task, he can add it to the dependencies of the project-wide lifecycle `check` task.
Meaning he can't model the fact that a particular *check* task is related to a particular component.
Adding it as a dependency of the component's `build` task makes no sense.
Adding it as a dependency of a test suite's `run` task makes no sense either.

This story is about adding the *checkability* concern to components.

### Test coverage

- [ ] Build user can invoke the `check` task of components
- [ ] Build author can add *check* tasks to components `check` task
- [ ] Components `check` tasks run component test suites
- [ ] Lifecycle `check` task depends on all components `check` tasks

### Implementation notes

- The change is on `platform-base`, native, jvm and play ecosystems should be impacted
- Introduce `CheckableComponentSpec`
- `BinarySpec extends BuildableComponentSpec, CheckableComponentSpec`
- `BinaryTaskCollection.getCheck()`
- Bind components `check` task to lifecycle `check` task
- Do not bind test suites `run` task to lifecycle `check` task anymore
- Bind test suites `run` task to tested component's `check` task, or directly to lifecycle `check` if they don't test a component

### Out of scope

- Tackling the terminology mismatch as this would result in breaking changes
- `Checkable` next to `Buildable` is not defined enough 
- `BinarySpec.isCheckable()`, will simply use `isBuildable()` for now


### (Story) Build dependents of a binary or component

Each binary and component should have a `buildDependents` task associated.
This task should assemble and check both the binary or component itself and all its dependent binaries.

### Test coverage

- [ ] Build dependents assemble all dependent components and run all dependent test suites
- [ ] Build dependents run *check* tasks associated with the component and all its dependents

### Implementation notes

- Create `buildDependents` tasks on all binaries
- `buildDependents` depends on its associated binary `check` task
- `buildDependents` depends on its direct dependents `check` tasks
- Component's `buildDependents` task depends on all the component's binaries `buildDependents` tasks.


## Open issues

`TBD`


## Out of scope

`TBD`
