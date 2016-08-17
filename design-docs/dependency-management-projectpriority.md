# Allow selecting project dependencies over binary dependencies

## High-level goals

Gradle normally handles version conflicts by picking the highest version a dependency.
In rare cases this may result in a project dependency being replaced by a binary dependency, which
may be counter-intuitive. The objective of this change is to make it 
configurable to always prefer project dependencies.

The situations where this happens boils down to the following two cases:

* Execute a composite build where a dependency between
  included builds is actually not included
* Test code changes in a multi project build on a common project and a project using
  this project where not all intermediate dependencies are project dependencies
  
This means the basic setup for the dependencies will be something like the following:

<pre>
    A ---> C
     \     ^
      \   /
       V /
        B
</pre>
  
Here `A` and `C` will be projects in a multi project build respectively included builds
in a composited build and `B` will
be a binary dependency.

We still want to keep the current behaviour for two reasons:

* Changing the default behavior would probably break some builds
* For use cases like performance testing it is still necessary to have
  a binary dependency on an earlier version of the same project which
  should not be replaced by the project dependency.
  
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

In more detail, let us assume that we have the following layout

    settings.gradle
    projectA
       build.gradle
    projectC
       build.gradle


`settings.gradle`:
   
    include ':projectA'
    include ':projectC'

`projectA/build.gradle`:

    plugins {
        id 'java'
        id 'maven-publish'
    }
    
    group = 'myorg.projectA'
    version = '1.5'
    
    dependencies {
        compile project(':projectC')
        compile 'myorg.projectB:projectB:1.7'
    }

`projectC/build.gradle`:

    plugins {
        id 'java'
        id 'maven-publish'
    }
    
    group = 'myorg.projectC'
    version = '1.7'

Let us assume that `projectB` has the maven coordinates `myorg.projectB:projectB:1.7`
and depends on `myorg.projectC:projectC:1.9`.

The default behaviour now is that `projectA` is compiled against `myorg.projectC:projectC:1.9`. If
we add

    configurations.all {
        resolutionStrategy {
            preferProjectModules()
        }
    }

to `projectA/build.gradle` then `projectA` is compiled against `project(:projectC)`.

### Implementation

A new class `ProjectDependencyForcingResolver` is introduced. It implements `ModuleConflictResolver`,
and is chained in front of `LatestModuleConflictResolver` by `DefaultArtifactDependencyResolver`.

The new method `ResolutionStrategy.preferProjectModules` is added which activates `ProjectDependencyForcingResolver`.

### Test coverage

`ProjectDependencyPreferenceIntegrationTest`, is added in order to test the various permutations of
versions, forced dependencies and `preferProjectModules` settings.

We need to test at least the following situations:

* `A -> B -> C` with `B` being an external module
* Some more complicated dependency graph with at least five dependencies
* Activating `preferProjectModules` for some projects and not for others
* The same tests in the composite build case
