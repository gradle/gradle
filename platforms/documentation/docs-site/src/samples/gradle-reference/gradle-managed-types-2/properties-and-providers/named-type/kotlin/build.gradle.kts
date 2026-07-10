interface MyNamedType : Named {

}

abstract class MyPluginExtension {
    abstract val myNamedContainer: NamedDomainObjectContainer<MyNamedType>

    fun myNamedContainer(configurationAction: Action<in NamedDomainObjectContainer<MyNamedType>>) = configurationAction.execute(myNamedContainer)
}

val pluginExtension = extensions.create<MyPluginExtension>("pluginExtension")

pluginExtension.apply {
    myNamedContainer {
        val myName by registering
    }
}
