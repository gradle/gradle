## Tooling client requests arbitrary model type for every project in a composite

### Overview

Previous stories have implemented hard-coded support only for EclipseProject models. This story adds support for the other 
standard models that are supported in the Tooling API.

Tooling API supports retrieving these model types:
- hierarchical model, a single model contains models for all projects.
  - EclipseProject
  - HierarchicalEclipseProject
  - GradleProject
  - BasicGradleProject
- hierarchical models where the model is returned for the root project. models for sub-projects are different type than the parent.
  - IdeaProject
  - BasicIdeaProject
- build models, a single model for the build
  - BuildEnvironment
  - GradleBuild
- project models, a model has to be requested separately for each project
  - BuildInvocations
  - ProjectPublications

### API

This story doesn't introduce any API changes.

### Implementation notes

The current implementation supports hierarchical models. Support for per-project models will be added.

Per-project models will be retrieved from each participant by using a custom BuildAction that traverses the build's project structure and uses the API method available on
`org.gradle.tooling.BuildController` to request per-project models:
```
<T> T getModel(Model target, Class<T> modelType) throws UnknownModelException;
```
The `getBuildModel` method on `BuildController` will be used to traverse the build's project structure.

Build models will be handled in the same way as per-project models in the implementation.
The same applies to `IdeaProject` and `BasicIdeaProject`, they will be handled as per-project models, returning the same instance for each project in the build. 
They are hierarchical models, but the children are different type than the parent.
It's really not a hierarchical structure since a build returns a `IdeaProject` with multiple `IdeaModule`s as children. The possible sub-project hierarchy is flattened.

### Test coverage

- given 4 scenarios of composites:
  - scenario #1, participant:
    - 1 single-project build
  - scenario #2, participant:
    - 1 multi-project build with 3 projects
  - scenario #3, participants:
    - 1 single-project build
    - 1 multi-project builds with 3 projects
- each sample project is a java project with publications for each project so that `ProjectPublications` can be tested.

- test that each model type can be retrieved 
  - check that there is a model result for each project for project models and normal hierarchical models
  - some model types don't contain the project information so that the model can be correlated to a project. 
    - In these cases, check that the number of returned models matches the number of projects
  - Build models and IdeaProject models are the same for each project in the build. 
    - In these cases, check that the number of returned models matches the number of builds in the composite
