Changes to improve build execution time when a build is mostly up-to-date. 

Audience is developers that are using the Gradle daemon.

- Review and update performance tests to measure this and lock in improvements.
- Profile to validate the following plan:
- Reuse the result of directory scanning
- Don't scan input directory multiple times when executing a task
- Improve in-heap cache management to evict entries that aren't likely to be used, such as when switching builds.
- Don't cache the result of `PatternSpec` evaluation. It's now faster to evaluate each time than cache the result
- Profile and improve dependency resolution speed, in particular when nothing has changed.
    - Reuse resolution result for configuration that has same inputs as another.
    - Reuse resolution result across builds.
    
### Dependency resolution result reuse    

Some notes:

Results are reused when all is true:

- dependency declarations are the same (including the attached artefact references and exclude rules, and those dependencies inherited from parent configurations).
- repository declarations are the same (including all settings such as url, patterns, credentials, and so on)

Results are not reused when any is true:

- there are project dependencies
- there are dynamic/changing dependencies
- there are resolution rules (includes forced versions)
- there are exclude rules
- there are module metadata rules (includes selection rules)
- there are different credentials
- resolution fails
