// create an adhoc component
val softwareComponentFactory = extensions.getByType(PublishingExtension::class.java).softwareComponentFactory
val adhocComponent = softwareComponentFactory.adhoc("myAdhocComponent")
// add it to the list of components that this project declares
components.add(adhocComponent)
// and register a variant for publication
adhocComponent.addVariantsFromConfiguration(outgoing) {
    mapToMavenScope("runtime")
}
