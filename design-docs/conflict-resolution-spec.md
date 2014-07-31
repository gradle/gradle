Improve conflict resolution to better deal with conflicts across different module name/group

# Use cases

1. 'com.google.collections:google-collections' and 'com.google.guava:guava' are conflicting, prefer guava.
2. 'org.jboss.netty:netty' and 'io.netty:netty*' are conflicting, prefer io.netty.
3. 'org.springframework:spring' and 'org.springframework:spring-*' are conflicting, prefer 'org.springframework:spring-*' (for example, spring-core).
4. A team in an organization decided to extract a standalone project out of a bigger project.
The group id of the modules moved to a new project needed to change.
Now there's a risk that consumers will have problems with conflict resolution.

# Implementation plan

There are two things we need to be able to do when traversing the graph:

1. Detect whether two different modules conflict with each other.
2. Decide which of a set of conflicting modules to select.

## Story: Allow declaring that certain module is replaced by some other.

DSL mock up:

    dependencies {
        components {
            module('com.google.guava:guava') {
                replacedBy 'com.google.collections:google-collections'
            }
        }
    }

This states that module 'com.google.guava:guava' was replaced by 'com.google.collections:google-collections' at some point. This implies
two things:

- Every version of 'google-collections' and every version of 'guava' conflict with each other.
- Every version of 'google-collections' is older than any version of 'guava'.

### User visible changes

1. New DSL API (see above)
2. Smarter conflict resolution, for example: no more guava and google-collections together in the classpaths.

### Implementation

- Extra input to DependencyGraphBuilder
- New incubating api in the 'components' DSL element

### Test coverage

- A replaces B - if both modules exist in the graph, only A is included in the graph
    - ensure jar B is not included in the result
- A does not replace B when only B module exists in the graph
- A replaces B when both are transitive dependencies
- A replaces B given traversal order:
    - A, B
    - B, A
- A replaces B:1.0 and B:2.0 given traversal order:
    - B:1.0, B:2.0, resolve conflict, A
    - A, B:1.0, B:2.0, resolve conflict
- A:2.0 replaces B:1.0, B:2.0, A:1.0 given traversal order:
    - A:2.0, B:1.0, B:2.0, resolve conflict, A:1.0
    - A:2.0, B:1.0, B:2.0, A:1.0, resolve conflict
    - A:1.0, B:1.0, B:2.0, resolve conflict, A:2.0
    - A:1.0, B:1.0, B:2.0, A:2.0, resolve conflict
    - A:1.0, A:2.0, resolve conflict, B:1.0, B:2.0
    - B:1.0, B:2.0, resolve conflict, A:1.0, A:2.0
- corner cases
    - A replaces B replaces C, all included in the graph, A is selected
- unhappy paths
    - A replaces B, B replaces A (cycle) - reasonable error is emitted
    - A replaces A - reasonable error
    - A:1->B:1->A:2, all in graph, A:2 and B:1 resolved
    - A:2->B:1->A:1, all in graph, error (cycle) because A:1 is conflict-resolved into A:2.
    - incorrect inputs: nulls, malformed notation, unsupported types

## Story: component replacement coexists with exclude rules

### Test coverage

- A does not replace B when both are in graph but A is excluded via an exclude rule
- A replaces B, B is excluded via rule, only A ends up in the graph

## Story: component replacement is honored by ResolvedConfiguration API

- A replaces B and ResolvedConfiguration API is still happy
- A replaces B, only A artifact is included in the ResolvedConfiguration's artifacts

## Story: component replacement is explicit in the dependency reports

- a:a:1.0 replaces b:b:1.0, the 'dependencies' report shows "a:a:1.0 -> b:b:1.0"
- a:a:1.0 replaces b:b:1.0, the 'dependencyInsight' report shows "a:a:1.0 -> b:b:1.0 (a:a replaces b:b)"
- A replaces B, ResolutionResult object contains correct selection reason for B

## Story: component replacement coexists with dependency resolve rules

### Test coverage

- A does not replace B when both are in graph but B is changed into a different module via dependency resolve rule
- A replaces B, B is turned into A via dependency resolve rule -> only A appears in the graph, selection reason for B is 'resolve rule'
- A replaces B when A is created from C via dependency resolve rule
- A replaces B when A is created from C and B from D via dependency resolve rules, selection reason for B is 'A replaces B'

## Story: Allow declaring module replacements via module specs

Make it possible to declare module replacements flexibly, so that sets of modules can be replaced.

### Use cases:

- modules replaced by a set of modules: spring -> spring-core, spring-aop, ...
- as above but starting from some version: groovy -> groovy, groovy-ant, groovy-xml only starting from 2.0
- a set of modules replaced by a single module (hypothetical)

DSL mock up:

    dependencies {
        components {
            module('org.springframework:spring') { ComponentMetaData details ->
                details.replacedBy 'org.springframework:spring-core'
                details.replacedBy 'org.springframework:spring-aop'
            }

            //More brainstorming - general DSL on a high level, ideas:
            modules(notation).all { SomeModuleMetadata details -> ... }
            modules.matching(notation).all { SomeModuleMetadata details -> ... }
            modules(notation) {}
            module(notation) {}
            //the 'modules' closure is evaluated during the configuration time
            //so it declares various information about the modules, this information is consumed during project configuration and later used during resolution

            //examples (some of them are far into the future)
            modules('org.springframework:spring').all {
                replacedBy 'org.springframework:spring-core'
                replacedBy 'org.springframework:spring-aop'
            }

            modules('org:api', 'com:impl').all {
                releasableUnit()
                //or
                requiresConsistentVersion()
                //or
                consistentVersion()
            }

            modules { ModuleSelector it -> it.group.startsWith('com.linkedin.') && it.hasSameGroup() }.all {
                releasableUnit()
            }

            modules('com.linkedin.*').all {
                releasableUnit()
            }

            modules { it.name == 'groovy-all' && it.numericVersion >= 2  }.all {
                replacedBy 'groovy-core'
                replacedBy 'groovy-xml'
                releasableUnit() //ensures 'groovy-core' and 'groovy-xml' will have consistent version
            }
        }
    }

This states that 'org.springframework:spring' was replaced by both 'org.springframework:spring-core' and 'org.springframework:spring-aop' at some point. This implies:

- Every version of 'org.springframework:spring' and every version of 'org.springframework:spring-core' conflict with each other.
- Every version of 'org.springframework:spring' and every version of 'org.springframework:spring-aop' conflict with each other.
- Every version of 'org.springframework:spring-core' is newer than every version of 'org.springframework:spring'.
- Every version of 'org.springframework:spring-aop' is newer than every version of 'org.springframework:spring'.
- When replacing a version of 'org.springframework:spring' due to a conflict, include both 'org.springframework:spring-core' and 'org.springframework:spring-aop'
in the result.

### Implementation plan:

- DependencyGraphBuilder receives Spec<ModuleIdentifier> information for deciding whether given modules are in conflict and for deciding who replaces them.

### Test coverage:

- A replaced by A-api, A-impl
    - When dependencies on A and A-api:1.2 are present in the graph, the result should contain A-api:1.2 and A-impl:1.2
    - When dependencies on A and A-api:1.2 and A-impl:1.3 are present in the graph, the result should contain A-api:1.3 and A-impl:1.3
- B-api, B-impl replaced by B
- A replaced by A-api, A-impl starting from version 2.0
- A replaced by B (rule1) and C (rule2). A,B,C in graph

# Open issues

- need to use the same version of A-api and A-impl regardless of whether A is in the graph or not. The DSL above doesn't capture this
- what do we do if 2 replacement rules match given component? Which rule should be chosen?
