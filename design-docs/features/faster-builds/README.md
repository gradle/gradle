## Goal

Make Gradle faster in many common cases.

Deliver the performance improvements that have already been implemented in the Gradle daemon
to more users, by enabling the Gradle daemon by default.

### Audience

The improvements are aimed at *developers* working in the Java and Android ecosystems and whose builds use the
current software model and and who are using the Gradle daemon (possibly implicitly through the IDE). 

The improvements will be general purpose and so will benefit all Gradle users, to greater and lesser degrees.

### Out of scope

Out-of-scope are the following improvements:

- Faster builds for other ecosystems: native, Play, Scala, Groovy, web.
- Faster builds for software model builds.
- Faster builds for build authoring and other use cases outside the dev loop.
- Faster builds for non-daemon usage.
- Making Java compilation faster, including improvements to incremental compile or adding compile avoidance.

Here 'out-of-scope' means only that these use cases won't be specifically prioritized. If these use cases happen to improve due to other work, then that's a good thing. 

TBD: 

- parallel execution in or out of scope?
- configure-on-demand in or out of scope?

## Features

- [ ] [Faster test execution startup](faster-test-execution-startup)
- [ ] [Faster build configuration](faster-build-configuration)
- [ ] [Faster incremental builds](faster-incremental-builds)
    - [ ] Improved file system scanning
    - [ ] Faster dependency resolution
- [ ] [Daemon on by default for all users](daemon-on-by-default)
    - [ ] Fix robustness and diagnostic issues that prevent the daemon to be enabled by default
    - [ ] Enable daemon self-monitoring
    - [ ] [Fix Windows specific blockers](daemon-on-by-default/windows-blockers)
    - [ ] Enable by default
    
TBD:    

- Finish parallel project execution and mark as stable?
- Switch on parallel project execution as default?
- Finish configure on demand and mark as stable?
- Switch on configure on demand as default?
