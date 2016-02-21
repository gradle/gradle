A series of features to make Gradle faster in many common cases. The audience is *developers* working in the Java and Android ecosystems and whose builds use the
current software model and and who are using the Gradle daemon (possibly via the IDE). 
However the improvements will be cross-cutting and will benefit all Gradle users, to greater and lesser degrees.

### Out of scope

Out-of-scope are the following improvements

- Making other ecosystems faster: native, Play, Scala, Groovy.
- Making software model builds faster.
- Making build authoring faster.
- Making non-daemon usage faster.

Here 'out-of-scope' means only that these use cases won't be specifically prioritized. If these use cases happen to improve thanks to other use cases, then that's a good thing. 

## Features

- [ ] [Faster test execution startup](faster-test-execution-startup)
- [ ] [Faster build configuration](faster-build-configuration)
- [ ] [Faster incremental builds](faster-incremental-builds)
    - [ ] Improved file system scanning
    - [ ] Faster dependency resolution
- [ ] [Daemon on by default](daemon-on-by-default)
    - [ ] Improved lifecycle
    - [ ] Fix Windows specific blockers
    