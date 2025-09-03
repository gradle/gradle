// tag::dos[]
abstract class MyPluginExtensionDomainObjectSet {
    // Define a domain object set to hold strings
    abstract val myStrings: DomainObjectSet<String>

    fun myStrings(action: Action<in DomainObjectSet<String>>) = action.execute(myStrings)
}

val dos = extensions.create<MyPluginExtensionDomainObjectSet>("dos")

dos.apply {
    myStrings {
        add("hello")
    }
}

// end::dos[]

require(dos.myStrings.size == 1)

// tag::ndos[]
// tag::ndol[]
// tag::ndoc[]
interface Person : Named {
    
}
// end::ndol[]
// end::ndoc[]

abstract class MyPluginExtensionNamedDomainObjectSet {
    // Define a named domain object set to hold Person objects
    abstract val people: NamedDomainObjectSet<Person> 

    fun people(action: Action<in NamedDomainObjectSet<Person>>) = action.execute(people)
}

val ndos = extensions.create<MyPluginExtensionNamedDomainObjectSet>("ndos")

ndos.apply {
    people {
       add(objects.newInstance<Person>("bobby"))
    }
}
// end::ndos[]

require(ndos.people.size == 1)

// tag::ndol[]

abstract class MyPluginExtensionNamedDomainObjectList {
    // Define a named domain object container to hold Person objects
    abstract val people: NamedDomainObjectList<Person> 

    fun people(action: Action<in NamedDomainObjectList<Person>>) = action.execute(people)
}

val ndol = extensions.create<MyPluginExtensionNamedDomainObjectList>("ndol")

ndol.apply {
    people {
        add(objects.newInstance<Person>("bobby"))
        add(objects.newInstance<Person>("hank"))
    }
}

// end::ndol[]

require(ndol.people.size == 2)

// tag::ndoc[]

abstract class MyPluginExtensionNamedDomainObjectContainer {
    // Define a named domain object container to hold Person objects
    abstract val people: NamedDomainObjectContainer<Person> 

    fun people(action: Action<in NamedDomainObjectContainer<Person>>) = action.execute(people)
}

val ndoc = extensions.create<MyPluginExtensionNamedDomainObjectContainer>("ndoc")

ndoc.apply {
    people {
        val bobby by registering 
        val hank by registering 
        val peggy by registering
    }
}
// end::ndoc[]

require(ndoc.people.size == 3)

// tag::epdoc[]
interface Animal : Named {

}

interface Dog : Animal {
    val breed: Property<String>
}

abstract class MyPluginExtensionExtensiblePolymorphicDomainObjectContainer {
    // Define a container for animals
    abstract val animals: ExtensiblePolymorphicDomainObjectContainer<Animal> 

    fun animals(action: Action<in ExtensiblePolymorphicDomainObjectContainer<Animal>>) = action.execute(animals)
}

val epdoc = extensions.create<MyPluginExtensionExtensiblePolymorphicDomainObjectContainer>("epdoc")

// Register available types for container
epdoc.animals.registerBinding(Dog::class, Dog::class)

epdoc.apply {
    animals {
        val bubba by registering(Dog::class) {
            breed = "basset hound"
        }
    }
}

// end::epdoc[]

require(epdoc.animals.size == 1)
