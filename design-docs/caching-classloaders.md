Cache classloaders to improve daemon's performance and memory consumption

# Use cases

- User facilitates the daemon more frequently without the need for restarting it.
    Classloaders caching improves daemon footprint and avoids memory leaks.
- User observes faster builds:
    - less memory used, less gc is needed, faster build
    - each consecutive build is faster thanks to jvm hotspot optimisations. This is possible because classes are reused.

# Implementation notes

- in Gradle codebase we avoid direct creation of ClassLoaders (new URLCLassloader()) and we use a service
    that can manage caching and decide whether to reuse or create new CL.
- classloaders need to be cached based on the classpath and the parent classloader; classpath of the classloader cache key needs to consider file hashes
- should classloaders caching consider the 'kind' of the classloader?
    Do we have a case that 2 classloaders have the same classpath and parent and we explicitly
- we probably need to avoid mutation of the classloaders. Instead, new classloader can be created based on an existing one, and the creation managed through a service.
- how do we roll out this change incrementally? It is a breaking change. Do we offer an opt-in for this feature?

## Story 1: Some thing

tbd.

### User visible changes

tbd.

### Implementation

tbd.

### Test coverage

tbd.

# Open issues

This section is to keep track of assumptions and things we haven't figured out yet.