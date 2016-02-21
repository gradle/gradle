Changes to improve build execution time when a build is mostly up-to-date. Audience is developers that are using the Gradle daemon.

- Review and update performance tests to measure this and lock in improvements.
- Profile to validate the following plan:
- Reuse the result of directory scanning
- Don't scan input directory multiple times when executing a task
- Don't cache the result of `PatternSpec` evaluation. It's now faster to evaluate each time than cache the result
- Profile and improve dependency resolution speed, in particular when nothing has changed.
