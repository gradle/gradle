package my

import org.gradle.api.*

// TIP #1: Provide documentation comments and they will be available in IntelliJ IDEA when editing the consumer
// project's build script.

/**
 * The documentation plugin. Configurable via the {@code documentation} extension.
 *
 * @see my.DocumentationExtension
 */
class DocumentationPlugin implements Plugin<Project> {

    void apply(Project project) {
        project.with {
            def books = container(Book)
            books.all { book ->
                book.sourceFile = file("src/docs/$name")
            }
            extensions.create("documentation", DocumentationExtension, books)
            tasks.create("books") { task ->
                task.doLast {
                    books.each { book ->
                        println "$book.name -> ${relativePath(book.sourceFile)}"
                    }
                }
            }
        }
    }
}

/**
 * Configuration options for the {@link my.DocumentationPlugin}.
 *
 * @see #getBooks
 */
// TIP #2: Make sure all types exposed in the API are publicly visible.
class DocumentationExtension {

    private final NamedDomainObjectContainer<Book> books

    DocumentationExtension(books) {
        this.books = books
    }

    /**
     * The book collection.
     */
    // TIP #3: Explicitly declare the full generic return type of any methods.
    NamedDomainObjectContainer<Book> getBooks() {
        books
    }

    // TIP #4: For best results, avoid declaring Action based configuration helpers for collections such as:
    //
    //   def books(Action<NamedDomainObjectContainer<Book>> action) { ... }
    //
    // First, they are unnecessary. The Kotlin DSL already treats any NamedDomainObjectContainer as configurable
    // via a lambda expression.
    //
    // Second, they might cause naming resolution conflicts as both `getBooks()` and `books(Action)` are visible as `books`
    // in Kotlin (incidentally, that's the reason behind the `(plugins) { ... }` and `(publications) { ... }` syntax in
    // this project's build.gradle.kts, to force the Kotlin compiler to resolve to the property instead of the method).
    //
    // A Closure based method would be ok as those are still required by the Groovy DSL and don't cause any conflict
    // in Kotlin.
}

/**
 * A book.
 */
class Book {

    /**
     * The name of the book.
     */
    final String name

    /**
     * The book source file.
     */
    File sourceFile

    Book(String name) {
        this.name = name
    }
}
