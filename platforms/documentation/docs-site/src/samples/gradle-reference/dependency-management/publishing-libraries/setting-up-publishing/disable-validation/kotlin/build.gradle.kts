tasks.withType<GenerateModuleMetadata>().configureEach {
    // The value 'enforced-platform' is provided in the validation
    // error message you got
    suppressedValidationErrors.add("enforced-platform")
}
