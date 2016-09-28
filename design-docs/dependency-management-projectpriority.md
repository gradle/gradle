# Allow selecting project dependencies over binary dependencies

## High-level goals

Gradle normally handles version conflicts by picking the highest version of a dependency.
In rare cases this may result in a project dependency being replaced by a binary dependency, which
may be counter-intuitive. The objective of this change is to make it 
configurable to always prefer project dependencies.

The basic setup for the dependencies will be something like the following.
Let us assume we have a dependency graph like this:

<pre>
ProjectA ─(project dependency)─> ModuleC       //parts of the build
 |
 └> ModuleB(1.0) ──────────────> ModuleC(2.0)  //from repository
</pre>

That means `ProjectA` depends on modules `MoudleB` and `ModuleC`, using a project dependency
to refer to a variant of `ModuleC` of unspecified version that is a project in the build.
The external `ModuleB:1.0` depends on `ModuleC:2.0`.
This leads to dependency conflicts between the different dependencies to `ModuleC`.
The goal of this feature is to introduce a conflict resolution mechanism that selects the
dependency based on the type of a dependency (is it a _module_ or a _project_ dependency?) 
rather than deciding primarily based on module versions.

The two situations where a dependency conflict as above occur are as follows.

### Multi project build 

Working in a multi project build on a common project and a project using
this project where not all intermediate dependencies are project dependencies.

A more concrete example:

We have a multi project build with two sub projects `ProjectA` and `ModuleC`.
`ProjectA` depends on `ModuleC` and binary `ModuleB:1.0`.
Binary `ModuleB:1.0` depends on binary `ModuleC:2.0`.

What is the runtime classpath of `ProjectA`? There is a version conflict between subproject
`ModuleC` and binary `ModuleC:2.0`. Based on our current rules we would pick binary `ModuleC:2.0`
if the subproject `ModuleC` defines a lower or no version or subproject `ModuleC` is it defines 
a version equal or higher to `2.0`. This can be counter intuitive, as it is not specified in the
project dependency itself, which version is desired. Thus we may prefer to always pick the project 
dependency `ModuleC`.   
 
### Composite build 

Working in a composite build on a common project and a project using
this project where not all intermediate dependencies are part of included builds.

A more concrete example:

We have a composite build with two included builds - `X` and `Y`.
The included build `X` has a project `ProjectA` which depends on `MoudleB:2.0` and `ModuleC:1.0`.
The included build `Y` has project `ModuleC:1.0`.
`MoudleB:2.0` depends on binary `MoudleC:2.0`.

What is the runtime classpath of project `ProjectA`? There is a version conflict between
project `ModuleC:1.0` and binary `ModuleC:2.0`. ~~Based on our current rules we would pick
binary ModuleC:2.0~~. _This is not true (anymore?) because the automatic dependency substitution
of composite builds already leads to the behavior that the project dependency is always picked._

### Current behavior  
  
We still want to keep the current behaviour for two reasons:

1. Backwards compatibility. Changing the default behavior would probably break some builds.

2. For use cases like performance testing it is still necessary to have
  a binary dependency on an earlier version of a common project which
  should not be replaced by the project dependency.
  For example given a performance test project `A` which has a project dependency
  on a common module `C`. I want to performance test `B`, which has a binary dependency
  on `C`. Then I want to test `B` with its binary dependency `C`.
  
## Story: User can prefer project modules for conflict resolution

### User visible changes

The user is able to do the following:

    configurations.all {
        resolutionStrategy {
            preferProjectModules()
        }
    }

When this method is called, project modules are preferred over binary modules
in conflict resolution.

In more detail, let us assume that we have the following layout:

    settings.gradle
    projectA
       build.gradle
    projectC
       build.gradle


`settings.gradle`:
   
    include ':projectA', ':projectC'

`projectA/build.gradle`:
    
    dependencies {
        compile project(':projectC')
        compile 'myorg.moduleB:moduleB:1.0'
    }

`projectC/build.gradle`:
    
    group = 'myorg.projectC'
    version = '1.0'

Let us assume that `moduleB` has the maven coordinates `myorg.moduleB:moduleB:1.0`
and depends on `myorg.projectC:projectC:2.0`.

The default behaviour now is that `projectA` is compiled against `myorg.projectC:projectC:2.0`. If
we add

    configurations.all {
        resolutionStrategy {
            preferProjectModules()
        }
    }

to `projectA/build.gradle` then `projectA` is compiled against `project(':projectC')`.

### Implementation

A new class `ProjectDependencyForcingResolver` is introduced. It implements `ModuleConflictResolver`,
and is chained in front of `LatestModuleConflictResolver` by `DefaultArtifactDependencyResolver`.

The new method `ResolutionStrategy.preferProjectModules` is added which activates `ProjectDependencyForcingResolver`.

### Test coverage

`ProjectDependencyPreferenceIntegrationTest`, is added in order to test the various permutations of
versions, forced dependencies and `preferProjectModules` settings.

We need to test at least the following situations:

* `A -> B -> C` with `B` being an external module
* Include tests that check the current behavior, i.e. selecting the binary dependency `C`
  over the project dependency `C`
* Some more complicated dependency graph with at least five dependencies
* Activating `preferProjectModules` for some projects and not for others
* Activating `preferProjectModules` on a configuration and then check that
  this has no effect on an configuration extending from it.
* The same tests in the composite build case
* The same tests for doing dependency substitution

### Open Issues

* Do we potentially also have a touch point here for the native support?
  We might have a similar situation with pre-built libraries.
