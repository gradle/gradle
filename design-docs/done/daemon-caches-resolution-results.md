There is a big speed improvement when dependency resolution results are cached in between build runs.
Spikes show 50-70% speed improvement for incremental build and 30-40% improvement for clean builds (that don't have tests).

Story: daemon caches resolution result model across builds

- initial story
- the feature is turned on automatically when class loader caching is enabled.
The will be no new special internal property.
- this is the initial story and the resolution results are cached and reused only for relatively simple inputs.
Results are reused when all is true:
  * dependency declarations are the same (including the attached artefact references and exclude rules, and those dependencies inherited from parent configurations).
  * repository declarations are the same (including all settings such as url, patterns, credentials, and so on)
- Results are not cached / reused when any is true:
  * there are project dependencies
  * there are dynamic/changing dependencies
  * there are resolution rules (includes forced versions)
  * there are exclude rules
  * there are module metadata rules (includes selection rules)
  * resolution fails
- subsequent stories will make the feature more robust and support caching results for all kinds of inputs to the dependency resolution

Test cases

- failed resolution
- cached for dependency declarations (various kinds of dependency declarations)
- cached for dependency declarations + repositories (various kinds of repo declarations)
- cached for dependency declarations + repositories
- not cached when any of the unsupported inputs are used
- file dependencies combined with repo dependencies
- just file dependencies
- localGroovy() / gradleApi() dependencies
- flat dir repository
- client module dependencies

Open questions

- memory consumption

