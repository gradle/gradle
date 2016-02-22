A series of features to make Gradle faster in many common cases.

### Audience

*Developers* working in the Java and Android ecosystems and whose builds use the
current software model and and who are using the Gradle daemon (possibly implicitly via the IDE). 

The improvements will be general purpose and so will benefit all Gradle users, to greater and lesser degrees.

Note that one of the goals of this work is to deliver to many more users the performance improvements that have already been implemented by the Gradle daemon, by enabling the
Gradle daemon by default.

### Out of scope

Out-of-scope are the following improvements:

- Making other ecosystems faster: native, Play, Scala, Groovy, web.
- Making software model builds faster.
- Making build authoring and other non-dev loop use cases faster.
- Making non-daemon usage faster.
- Making specific task implementations faster, such as incremental Java compilation.

Here 'out-of-scope' means only that these use cases won't be specifically prioritized. If these use cases happen to improve due to work on other use cases, then that's a good thing. 

TBD: 

- parallel execution in scope or out of scope?
- configure-on-demand in scope or out of scope?

## Features

- [ ] [Faster test execution startup](faster-test-execution-startup)
- [ ] [Faster build configuration](faster-build-configuration)
- [ ] [Faster incremental builds](faster-incremental-builds)
    - [ ] Improved file system scanning
    - [ ] Faster dependency resolution
- [ ] [Daemon on by default](daemon-on-by-default)
    - [ ] Fix robustness and diagnostic issues that prevent the daemon to be enabled by default
    - [ ] Enable daemon self-monitoring
    - [ ] Fix Windows specific blockers
    - [ ] Enable by default
    
TBD:    

- Finish parallel project execution and mark as stable.
- Switch on parallel project execution as default.
- Finish configure on demand and mark as stable.
- Switch on configure on demand as default.
