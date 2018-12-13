// Sample from
//   https://docs.gradle.org/3.3/userguide/custom_plugins.html#sec:maintaining_multiple_domain_objects
// ported to Kotlin

apply<DocumentationPlugin>()

val books: NamedDomainObjectContainer<Book> by extensions

books {
    register("quickStart") {
        sourceFile = file("src/docs/quick-start")
    }
    register("userGuide") {

    }
    register("developerGuide") {

    }
}

tasks {
    register("books") {
        doLast {
            books.forEach { book ->
                println("${book.name} -> ${relativePath(book.sourceFile)}")
            }
        }
    }
}

class DocumentationPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val books = project.container(Book::class) { name ->
            Book(name, project.file("src/docs/$name"))
        }
        project.extensions.add("books", books)
    }
}

data class Book(val name: String, var sourceFile: File)
