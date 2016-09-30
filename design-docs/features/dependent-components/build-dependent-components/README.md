# Build dependent components

This feature allows a developer to trigger the assembly and check of given components and all the components that depend on them.


## Stories

### (Story) Assemble dependents of a binary

Each binary should have an `assembleDependents` task associated.
This task should assemble both the binary itself and all its dependent components.

### Test coverage

- [x] Assemble dependents in a single project build
- [ ] Assemble dependents in a multi project build
- [x] Assemble dependents test suites
- [ ] Assemble dependents skip non-buildable components

### Implementation notes

- Create `assembleDependents` tasks on all binaries
- `assembleDependents` depends on its associated binary `build` task
- `assembleDependents` depends on its direct dependents `build` tasks


### (Story) Assemble dependents of a component

Each component should have an `assembleDependents`  task associated.
This task should assemble all the component's binaries and all their dependent components.

### Test coverage

- [x] Assemble dependents of all buildable binaries of the targeted component

### Implementation notes

- Component's `assembleDependents` task depends on all the component's binaries `assembleDependents` tasks.


## (Story) Checkable binaries

The `LifecycleBasePlugin` registers project-wide lifecycle tasks named `clean`, `assemble`, `check` and `build`. The latter, that is `build`, depends on both `assemble` and `check`.

Software model buildable components/binaries register their `build` task as a dependency of the lifecycle `assemble` task. 
There's a terminology mismatch here, it should be *assemblable* components having an `assemble` task.

Test suites have a `run` task to actually run the tests. It is bound to the lifecycle `check` task.

If a build author wants to add some *check* tasks, let's say some *lint* task, he can add it to the dependencies of the project-wide lifecycle `check` task.
Meaning he can't model the fact that a particular *check* task is related to a particular component/binary.
Adding it as a dependency of the component's or binary's `build` task makes no sense.
Adding it as a dependency of a test suite's `run` task makes no sense either.

This story is about adding the *checkability* concern to binaries.
For components, see the next story.

### Test coverage

- [x] Executing the `check` task of a test suite binary runs the test suite
- [x] Executing the `check` task of a tested binary runs the associated test suite(s)
- [x] Custom *check* tasks bound to a binary's `check` task are run when executing the binary's `check` task
- [x] Executing the lifecycle `check` task runs all binaries `check` tasks
- [ ] Tests cover Native, Jvm and Play ecosystems

### Implementation notes

- Native, Jvm and Play ecosystems should be impacted
- Introduce `CheckableComponentSpec extends ComponentSpec`
- `BinarySpec extends BuildableComponentSpec, CheckableComponentSpec`
- `AbstractBuildableComponentSpec implements BuildableComponentSpec, CheckableComponentSpec`
- `BinaryTaskCollection.getCheck()`
- Promote `[Native|Jvm]TestSuiteBinarySpec.getTestedBinary()` into `TestSuiteBinary`
- Create `check` task for all binaries, including test suites binaries
- Remove the existing binding of test suites binaries `run` task to the lifecycle `check` task
- Bind test suites binaries `run` task to their `check` task
- Bind `check` task of all binaries to the lifecycle `check` task ; if it is a test suite binary and it has a tested binary then bind it to the tested binary's `check` task instead

### Out of scope

- Tackling the terminology mismatch as this would result in breaking changes
- `Checkable` next to `Buildable` is not defined enough at this stage nor is `BinarySpec.isCheckable()`, will simply use `isBuildable()` for now


## (Story) Checkable components

This story is about adding the *checkability* concern to components.
See the previous story about binaries.

### Test coverage

- [ ] Executing the `check` task of a test suite runs the `check` task of all its binaries
- [ ] Executing the `check` task of a tested component runs the `check` tasks of all the associated test suite(s) binaries
- [ ] Custom *check* tasks bound to a component's `check` task are run when executing the component's `check` task
- [ ] Executing the lifecycle `check` task runs all components `check` tasks
- [ ] Tests cover Native, Jvm and Play ecosystems

### Implementation notes

- Native, Jvm and Play ecosystems should be impacted
- `GeneralComponentSpec extends CheckableComponentSpec`
- Implement `CheckableComponentSpec` in `BaseComponentSpec`
- Create `check` task for all components, including test suites
- Remove the binding from binaries `check` tasks to the lifecycle `check` task introduced in the previous story
- For each component, bind the `check` task of all its binaries to its `check` task
- Bind the `check` task of all components to the lifecycle `check` task


### (Story) Build dependents of a binary or component

Each binary and component should have a `buildDependents` task associated.
This task should assemble and check both the binary or component itself and all its dependent binaries.

### Test coverage

- [x] Build dependents assemble all dependent components and run all dependent test suites
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
