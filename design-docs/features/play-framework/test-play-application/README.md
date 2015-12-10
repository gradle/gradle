# Developer tests Play application

- [x] Developer tests a basic Play application
- [ ] Developer configures a new test suite using the model

#### Later stuff

- Native integration with Specs 2

## Stories

### Story: Developer tests a basic Play application

- Add a `Test` test task per binary that runs the play application tests based on JUnit TestRunner against the binary
- The test sources are compiled against the binary and the `play-test` dependency based on play and scala version of the platform
- Can execute Play unit and integration tests
- Fails with a nice error message
- Wired into the `check` and `test` lifecycle tasks

#### Test cases
- Verify that running `gradle testBinary` and `gradle test` executes tests in the `/test` directory
- Verify output and reports generated for successful tests
- Verify output and reports generated for failing tests

### Story: Developer configures a new test suite using the model

Currently only a single Play Test suite is added to the Play model.  This would allow additional test suites to be easily configured
via a model element.

- Introduce a "test suite" model element that models a test suite with configurable source sets.
- Derive all necessary tasks off of the source set.

## Later features and stories

### Native integration with Specs 2

Introduce a test integration which allows Specs 2 specifications to be executed directly by Gradle, without requiring the use of the Specs 2
JUnit integration.

- Add a Specs 2 plugin
- Add some Specs 2 options to test tasks
- Detect specs 2 specifications and schedule for execution
- Execute specs 2 specifications using its API and adapt execution events

Note: no changes to the HTML or XML test reports will be made.

