class Book(val name: String) {
    var sourceFile: File? = null
}

class DocumentationPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        // Create a container of Book instances
        val books = project.container<Book>()
        books.all {
            sourceFile = project.file("src/docs/$name")
        }
        // Add the container as an extension object
        project.extensions.add("books", books)
    }
}

apply<DocumentationPlugin>()

// Configure the container
val books: NamedDomainObjectContainer<Book> by extensions

books {
    create("quickStart") {
        sourceFile = file("src/docs/quick-start")
    }
    create("userGuide")
    create("developerGuide")
}

tasks.register("books") {
    doLast {
        books.forEach { book ->
            println("${book.name} -> ${book.sourceFile}")
        }
    }
}
