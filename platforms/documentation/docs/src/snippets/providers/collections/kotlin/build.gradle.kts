// tag::dos[]
abstract class MyPluginExtensionDomainObjectSet {
    // Define a domain object set to hold strings
    val myStrings: DomainObjectSet<String> = project.objects.domainObjectSet(String::class)

    // Add some strings to the domain object set
    fun addString(value: String) {
        myStrings.add(value)
    }
}
// end::dos[]

// tag::ndos[]
// tag::ndol[]
// tag::ndoc[]
abstract class Person(val name: String)
// end::ndol[]
// end::ndoc[]

abstract class MyPluginExtensionNamedDomainObjectSet {
    // Define a named domain object set to hold Person objects
    private val people: NamedDomainObjectSet<Person> = project.objects.namedDomainObjectSet(Person::class)

    // Add a person to the set
    fun addPerson(name: String) {
        people.plus(name)
    }
}
// end::ndos[]

// tag::ndol[]

abstract class MyPluginExtensionNamedDomainObjectList {
    // Define a named domain object list to hold Person objects
    private val people: NamedDomainObjectList<Person> = project.objects.namedDomainObjectList(Person::class)

    // Add a person to the container
    fun addPerson(name: String) {
        people.plus(name)
    }
}
// end::ndol[]

// tag::ndoc[]

abstract class MyPluginExtensionNamedDomainObjectContainer {
    // Define a named domain object container to hold Person objects
    private val people: NamedDomainObjectContainer<Person> = project.container(Person::class)

    // Add a person to the container
    fun addPerson(name: String) {
        people.create(name)
    }
}
// end::ndoc[]

// tag::epdoc[]
abstract class Animal(val name: String)

class Dog(name: String, val breed: String) : Animal(name)

abstract class MyPluginExtensionExtensiblePolymorphicDomainObjectContainer(objectFactory: ObjectFactory) {
    // Define a container for animals
    private val animals: ExtensiblePolymorphicDomainObjectContainer<Animal> = objectFactory.polymorphicDomainObjectContainer(Animal::class)

    // Add a dog to the container
    fun addDog(name: String, breed: String) {
        var dog : Dog = Dog(name, breed)
        animals.add(dog)
    }
}
// end::epdoc[]
