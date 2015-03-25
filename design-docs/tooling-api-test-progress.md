
# Tooling API client listens to test execution progress.

## Test cases

- Can receive test events when running a build.
- Can receive test events when requesting a model.
- Can receive test events when running a custom build action.
- Can receive test events from multiple test tasks.
- Can receive test events when tests run in parallel.
- Useful behaviour when a client provided test listener throws an exception
- Useful behaviour when a client provided progress listener throws an exception
- Can receive events from tests in `buildSrc`
- Can receive events from tests in builds run using `GradleBuild`
- Can receive events from tests in builds run from build using tooling API
- Receives 'finished' test events when build is cancelled or daemon crashes
- Receives 'finished' progress events when build is cancelled or daemon crashes
