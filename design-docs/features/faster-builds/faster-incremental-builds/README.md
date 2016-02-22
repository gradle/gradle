Changes to improve build execution time when build outputs are mostly up-to-date. 

Audience is developers that are using the Gradle daemon.

### Implementation plan

- Review and update performance tests to measure this and lock in improvements.
    - Incremental build for large Java project
    - Incremental build for project with large dependency graph
- Profile test builds and use results to select improvements to implement 
        
### Potential improvements
    
Up-to-date checks    

- Fix hotspots identified by profiling
- Reuse the result of directory scanning
- Don't scan input directory multiple times when executing a task
- Improve in-heap cache management to evict entries that aren't likely to be used, such as when switching builds.
- Don't cache the result of `PatternSpec` evaluation. It's now faster to evaluate each time than cache the result

Dependency resolution

- Fix hotspots identified by profiling
- Reuse resolution result for configuration that has same inputs as another.
- Reuse resolution result across builds.
    
## Notes    

Various notes collected from old design specs.

### Dependency resolution result reuse    

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
