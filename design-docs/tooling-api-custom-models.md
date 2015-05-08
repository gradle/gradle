
## Story: Gradle plugin provides a custom tooling model to the tooling API client

Allow a plugin to expose a tooling model to any tooling API client that shares compatible model classes.

1. Add a public API to allow a plugin to register a tooling model to make available to the tooling API clients.
2. Move core tooling model implementations to live with their plugin implementations.
3. Custom plugin classpath travels with serialized model object back to the provider.

### Test cases

- Client requests a tooling model provided by a custom plugin.
- Client receives a reasonable error message when:
    - Target Gradle version does not support custom tooling models. Should receive an `UnknownModelException`
    - No plugin in the target build provides the requested model. Should receive an `UnknownModelException`.
    - Failure occurs when the plugin attempts to build the requested model.
    - Failure to serialize or deserialize the requested model.
- Generalise `UnsupportedModelFeedbackCrossVersionSpec`.
- Plugin attempts to register a model that some other plugin already has registered.

## Story: Plugin implements custom tooling model using rules

Allow a plugin to define a rule that builds a project scoped tooling model. Tooling model is some arbitrary serializable type.

An example:

    class SomeType implements Serializable {
        ...
    }

    class SomePlugin extends RuleSource {
        @ToolingModel
        public SomeType buildSomeModel(SomeInput input) {
            // Method creates an instance of SomeType, configured as per the input, and returns the result
        }
    }

### Implementation

A rule annotated with `@ToolingModel` will register the appropriate tooling model builder, and the rule will be
invoked when the given tooling model is requested for the project. As for all rules, the inputs will be
configured prior to invocation of the rule.

A tooling model rule can take some other tooling model as input, in addition to any model object.

It will be an error for multiple plugins to define the same model type in the same project.

### Test coverage

- Rule is invoked with its inputs when tooling model is requested.
- Rule is not invoked when different tooling model is requested.
- Rule is not invoked when tooling model is requested on another project.
- Rule can take another tooling model as input.
- Reasonable error message when:
    - Another plugins declares the same tooling model.
    - Another plugins registers a `ToolingModelBuilder` for the same tooling model.
    - Rule throws exception.
    - Inputs to rule cannot be provided in some way.
    - Tooling model cannot be serialized or deserialized.

## Story: Plugin implements custom tooling model using managed types

Allow a plugin to define a tooling model using a managed type.

An example:

    @Managed
    interface SomeType {
        // Some properties
    }

    class MyPlugin extends RuleSource {
        @ToolingModel
        public void buildSomeModel(SomeType model, SomeInput input) {
            // Configures the model instance based on the inputs
        }
    }

### Implementation

A view of `SomeType` is provided and the state is serialized across to the tooling API client.

Managed model may reference unmanaged properties, which must be serializable.

### Test coverage

- Model is serialized across to tooling API client, and adapted to client view.
- Rule can take another managed tooling model as input.
- A model can include nested elements.
- Reasonable error message when:
    - Unmanaged value referenced by model cannot be serialized or deserialized.
    - Model cannot be adapted to client view.
    - First parameter type is not `@Managed`
- The model cannot be mutated after the rule has completed.
