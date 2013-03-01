Improve the performance of dependency resolution

# Use cases

* Faster builds
* Much faster incremental builds
* Gradle build snappiness == developer happiness

# Implementation plan

## Reduce locking for local repositories

An introduction to the story.

### User visible changes

Faster Gradle

### Sad day cases

-

### Test coverage

Tighten the performance tests thresholds

### Implementation approach

Avoid artifact cache unlocking/locking for local repositories. Local repos bypass the artifact cache anyway.

## Story 2: Some other thing

### User visible changes

TBD

### Sad day cases

TBD

### Test coverage

TBD

### Implementation approach

TBD

# Open issues

This stuff is never done. This section is to keep track of assumptions and things we haven't figured out yet.
