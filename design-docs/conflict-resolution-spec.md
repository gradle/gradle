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

# Feature: Conflict resolution replaces a module or modules with a single module

## Story: Allow declaring that certain module is replaced by some other.

DSL mock up:

    dependencies {
        components.module('com.google.collections:google-collections').replacedBy('com.google.guava:guava')
    }

This states that module 'com.google.collections:google-collections' was replaced/superseded/deprecated by 'com.google.guava:guava' at some point. This implies
two things:

- Every version of 'google-collections' and every version of 'guava' conflicts with each other.
- Every version of 'google-collections' is older than any version of 'guava'.

### User visible changes

1. New DSL API (see above)
2. Smarter conflict resolution, for example: no more guava and google-collections together in the classpaths.

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
    - incorrect inputs: nulls, malformed notation, unsupported types
    - the replacement target is unresolved
    - the replacement source is unresolved

## Story: component replacement coexists with exclude rules

### Test coverage

- A does not replace B when both are in graph but A is excluded via an exclude rule
- A replaces B, B is excluded via rule, only A ends up in the graph

## Story: component replacement is honored by ResolvedConfiguration API

- A replaces B and ResolvedConfiguration API is still happy
- A replaces B, only A artifact is included in the ResolvedConfiguration's artifacts

## Story: component replacement coexists with dependency resolve rules

### Test coverage

- A does not replace B when both are in graph but B is changed into a different module via dependency resolve rule
- A replaces B, B is turned into A via dependency resolve rule -> only A appears in the graph, selection reason for B is 'resolve rule'
- A replaces B when A is created from C via dependency resolve rule
- A replaces B when A is created from C and B from D via dependency resolve rules, selection reason for B is 'A replaces B'

## Story: multiple modules are replaced by a single module

Make following possible:

    dependencies {
        components.module('com:a').replacedBy('com:c')
        components.module('com:b').replacedBy('com:c')
    }

### Test coverage

- a replaced by c (a->c), b->c, all a,b,c in graph, only c is resolved
- a->c, b->c, only a,b in graph, only c is resolved
- a->c, b->c, only a,c in graph, only c is resolved

# Feature: Conflict resolution replaces a module or modules with a set of modules

## Story: Allow declaring multiple module replacements

Make it possible to declare module multiple replacement targets for single replacement source.

### Use cases:

- modules replaced by a set of modules: spring -> spring-core, spring-aop, ...
- as above but starting from some version: groovy -> groovy, groovy-ant, groovy-xml only starting from 2.0
- a set of modules replaced by a single module (hypothetical, not covered by this story)

DSL mock-up:

    dependencies {
        components {
            module('org.springframework:spring') { ModuleMetadataDetails details ->
                details.replacedBy 'org.springframework:spring-core'
                details.replacedBy 'org.springframework:spring-aop'
            }

This states that 'org.springframework:spring' was replaced by both 'org.springframework:spring-core' and 'org.springframework:spring-aop' at some point. This implies:

- Every version of 'org.springframework:spring' and every version of 'org.springframework:spring-core' conflict with each other.
- Every version of 'org.springframework:spring' and every version of 'org.springframework:spring-aop' conflict with each other.
- Every version of 'org.springframework:spring-core' is newer than every version of 'org.springframework:spring'.
- Every version of 'org.springframework:spring-aop' is newer than every version of 'org.springframework:spring'.
- When graph contains:
    - all 3 modules, include only 'org.springframework:spring-core' and 'org.springframework:spring-aop'
    - 'spring' and 'spring-core', include only 'spring-core'
    - 'spring' and 'spring-aop', include only 'spring-aop'

### Test coverage:

- A replaced by A-api, A-impl
    - When dependencies on A and A-api:1.2 are present in the graph, the result should contain A-api:1.2 and A-impl:1.2
    - When dependencies on A and A-api:1.2 and A-impl:1.3 are present in the graph, the result should contain A-api:1.3 and A-impl:1.3

uhappy paths:
- cycle between newly added dependency
- dependency added by the mechanism is unresolved

# Feature: Better reporting of module replacements

## Story: component replacement is explicit in the dependency reports

It's an open question whether we want to implement this story.
The existing presentation in the reports treats the replacements as conflict resolution which technically is correct
because the module replacement declarations are an input to conflict resolution.
The current model of a single selection reason attached to the module is not enough for real world use cases.
Resulting resolved module is possibly a product of multiple resolution rules and multiple replacement declarations, possibly interleaving.
Down the road we need a mechanism that describes/lists all the manipulations done to the module (model rules will address that).

- a:a:1.0 replaces b:b:1.0, the 'dependencies' report shows "a:a:1.0 -> b:b:1.0"
- a:a:1.0 replaces b:b:1.0, the 'dependencyInsight' report shows "a:a:1.0 -> b:b:1.0 (a:a replaces b:b)"
- A replaces B, ResolutionResult object contains correct selection reason for B

# Open issues

- need to use the same version of A-api and A-impl regardless of whether A is in the graph or not. The DSL above doesn't capture this
- what do we do if 2 replacement rules match given component? Which rule should be chosen?

# DSL brainstorming

DSL brainstorming, other ideas:

    dependencies {
        components {
            //use 'modules' instead of 'module'
            modules(notation).all { SomeModuleMetadata details -> ... }
            modules.matching(notation).all { SomeModuleMetadata details -> ... }
            modules(notation) {}

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

More brainstorming, based on an existing API for configuring module metadata:

    dependencies {
        components {
            eachComponent { ComponentMetadataDetails details ->
                if (details.id.group == 'com.google.google-collections' && details.id.name == 'google-collections') {
                    details.replacedBy 'com.google.guava:guava'
                }
            }
            eachComponent { ComponentMetadataDetails details ->
                if (details.id.group == 'org.springframework' && details.id.name == 'spring') {
                    details.replacedBy 'org.springframework:spring-core', 'org.springframework:spring-aop'
                }
            }
            eachComponent { ComponentMetadataDetails details ->
                if (details.id.group == 'com.foo' && details.id.name == 'foo-impl') {
                    //api and impl will use consistent version
                    details.bundledWith 'com.foo:foo-api'
                }
            }
            eachComponent { ComponentMetadataDetails details ->
                if (details.id.group.startsWith('com.linkedin.')) {
                    //all components from the matching group will use consistent version:
                    details.bundledWith details.id.group
                }
            }
            eachComponent { ComponentMetadataDetails details ->
                //all components from the same 'group' will use consistent version:
                details.bundledWith details.id.group
            }
            eachComponent { ComponentMetadataDetails details ->
                if (details.id.group == 'org.springframework') {
                    details.bundledWith 'org.springframework'
                    if (details.id.name == 'spring') {
                        details.replacedBy { it.group == 'org.springframework' && it.name.startsWith('spring-') }
                    }
                }
            }
        }
    }
