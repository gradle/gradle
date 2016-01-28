## Tooling models for composite are produced by a single daemon instance

### Overview

This story moves the coordination of builds within a composite out of the Tooling API client and into a separate daemon process. This is useful for a number of reasons:
- Build coordination may be slow or consume a lot of memory: keeping it in a process separate from the IDE process is good form
- Coordinating builds will likely require tight integration with the participating build processes. Moving the coordination into a daemon process may assist with this tight integration, without necessarily requiring public, cross-version protocols.
- Command-line usage of composite builds will likely share the same daemon infrastructure, without requiring the use of the cross-version Tooling API protocol.

A [previous story](../multiple-builds#tooling-client-provides-model-for-composite-containing-multiple-participants) implemented basic composite build coordination within the Tooling API client process. Keeping the same API on the client, this story moves the implementation of this coordinator into a `BuildActionExecuter` implementation which resides in the daemon process.

For this story:

- The only supported model type is `EclipseProject`.
- The version of Gradle used for the coordinator process will not be user-configurable, and will match the Gradle version of the Tooling API client. (It may be necessary to provide an internal API for specifying a local Gradle installation to use.)
- Models for the actual participant builds will be obtained via separate Tooling API connections (no change from previous story)

### API

This story doesn't change the API of the composite build from previous stories.

### Implementation notes

On the client side, the idea is to extend the current Tooling API client and reuse the `ConsumerConnection` infrastructure.
The implementation involves the Tooling API client "consumer" and "provider" parts besides the daemon part. The provider and daemon execution code is in the launcher module.

The execution of composite build models needs a composite build specific `BuildActionExecuter`.
The contextual information of the composite build, like participant builds, should be passed in the `ConsumerOperationParameters`/`ProviderOperationParameters` parameter. These parameters should be mapped to a `BuildActionParameters` implementation that contains the composite build related contextual information in an instance of a `CompositeParameters` type. When the composite parameters are available, the `BuildActionExecuter` implementation should be chosen accordingly.

Internally a `SetOfEclipseProjects` type will be used to workaround the lack of support for collection types in existing infrastructure.

The launcher module already depends on tooling-api and core modules. Therefore using the tooling api for accessing participant projects should be possible.

### Test coverage

- Existing test coverage added in the previous composite build stories should pass.
    - This should include cross-version testing
- Check that retrieving a model causes a daemon to be started for the participant
    - the test code can use `DaemonLogsAnalyzer` for inspecting daemons

### Documentation

### Open issues

- Should we spawn a daemon process to 'coordinate' a composite build with a single participant?
  - If a composite build is declared with a single participant, no _coordination_ is needed.
- Do we need an 'embedded' implementation of the coordinator that is used for development?
    - Faster testing of composite build features
    - Debugging of composite build features
- Using the Tooling API to interact with participants with the same Gradle version as the coordinator seems unnecessary. Probably a separate story to re-use the daemon process for participants with the same Gradle version.
- Plan unit test coverage required for possible Tooling API consumer infrastructure changes
