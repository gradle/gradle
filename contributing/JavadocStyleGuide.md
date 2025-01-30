# Gradle Javadoc Style Guide

## 1.1 Formatting

### 1.1.1 General form

The basic formatting of Javadoc blocks is as seen in this example:

```java
/**
 * Returns an Image object that can then be painted on the screen.
 * <p>
 * The url argument must specify an absolute {@link URL}. 
 * The name argument is a specifier that is relative to the url argument.
 * This method always returns immediately, whether or not the image exists. 
 *
 * @param url an absolute URL giving the base location of the image
 * @param name the location of the image, relative to the url argument
 * @return the image at the specified URL
 * @see Image
 */
public Image getImage(URL url, String name) {
   try {
       return getImage(new URL(url, name));
   } catch (MalformedURLException e) {
       return null;
   }
}
```

Basic formatting rules:

- The first line contains the begin-comment delimiter ( `/**`).
- The first sentence is a summary.
- Notice the inline tag `{@link URL}`, which converts to an HTML hyperlink pointing to the documentation for the URL class.
- If you have more than one paragraph in the doc comment, separate the paragraphs with a `<p>` paragraph tag, as shown.
- Insert a blank comment line between the description and the list of tags, as shown.
- The first line that begins with an `@` character ends the description; you cannot continue the description following block tags.
- Block tags must be added in order.
- The last line contains the end-comment delimiter ( `*/`).

So lines won't wrap, limit any doc-comment lines to 120 characters.

### 1.1.2 HTML

Javadoc permits only a subset of HTML tags: `"a", "abbr", "acronym", "address", "area", "b", "bdo", "big", "blockquote", "br", "caption", "cite", "code", "colgroup", "dd", "del", "dfn", "div", "dl", "dt", "em", "fieldset", "font", "h1", "h2", "h3", "h4", "h5", "h6", "hr", "i", "img", "ins", "kbd", "li", "ol", "p", "pre", "q", "samp", "small", "span", "strong", "sub", "sup", "table", "tbody", "td", "tfoot", "th", "thead", "tr", "tt", "u", "ul", "var"`.

You can view the latest and full list of accepted tags in [`doclint/HtmlTag.java`](https://docs.oracle.com/en/java/javase/11/docs/api/java.desktop/javax/swing/text/html/HTML.Tag.html) for your JDK of choice.

Attempt to limit your usage of HTML to: `"a", "h1", "h2", "h3", "h4", "em", "li", "ol", "p", "ul"`.

Some HTML element types allow authors to omit end tags: `<p>` and `<li>`.

### 1.1.3 Paragraphs

One blank line—that is, a line containing only the aligned leading asterisk (`*`)—appears between the last paragraph and before the group of block tags if present:

```java
/**
 * that draw the image will incrementally paint on the screen.
 *
 * @param  url  an absolute URL giving the base location of the image
 */
```

Each paragraph is denoted by a `<p>` which is placed on a separate line:

```java
/**
 * argument is a specifier that is relative to the url argument.
 * <p>
 * This method always returns immediately, whether or not the
 */
```

HTML tags for other block-level elements, such as `<ul>` or `<table>`, are not preceded with `<p>`.

### 1.1.4 Symbols

Entities for the less than symbol (<) and the greater than symbol (>) should be written as `&lt`; and `&gt;`. 
Similarly, the ampersand (&) should be written as `&amp;`:

```java
/**
 * This &amp; that.
 */
```

### 1.1.5 Links

The `{@link <class or method reference>}` tag is specifically used to link to the Javadoc of other classes and methods:

```java
/**
 * The url argument must specify an absolute {@link classname}. The name
 */
```

To add a reference to an external URL, use `<a href="URL#value">label</a>`.
This adds a link as defined by `URL#value`.
The `URL#value` is a relative or absolute URL:

```java
/**
 * This is a link to <a href="http://www.google.com">Google</a>. The name
 */
```

### 1.1.6 Code keywords, names, and variables

Use `{@code ... }` style for keywords and names including:
- Java keywords
- package names
- class names
- method names
- interface names
- field names
- argument names
- code examples (see section 1.3 below)

For example:

```java
/**
 * Use the {@code Project} instance to configure the project.
 */
```

### 1.1.7 Block tags

Any of the standard "block tags" that are used appear in the order `@param`, `@return`, `@throws`, `@deprecated`, and these four types never appear with an empty description:

```java
/**
 * @param url an absolute URL giving the base location of the image
 * @param name the location of the image, relative to the url argument
 * @return the image at the specified URL
 * @see Image
 */
```

When a block tag doesn't fit on a single line, continuation lines are indented four (or more) spaces from the position of the `@`:

```java
/**
 * @param url an absolute URL giving the base location of the image, and 
 *            it is continuing the description on this new line
 * @param name the location of the image, relative to the url argument
 */
```

Full list of tags (in order):

| #  | Tag           | Usage                                                                          | Notes                                                                                                                                                                                                              |
|----|---------------|--------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1  | `@param`      | Methods and constructors only                                                  | Include this if applicable                                                                                                                                                                                         |
| 2  | `@return`     | Methods only                                                                   | Include this if applicable                                                                                                                                                                                         |
| 3  | `@throws`     | Same as `@exception`                                                           | Include this if applicable                                                                                                                                                                                         |
| 4  | `@see`        | Adds a "See Also" heading with a link or text entry that points to a reference | `@see string`<br>`@see <a href="URL#value">label</a>`<br>`@see package.class#member label`                                                                                                                         |
| 5  | `@since`      | Adds a "Since" heading                                                         | Include the Gradle version if applicable                                                                                                                                                                           |
| 6  | `@deprecated` | Adds a comment indicating that this API should no longer be used               | Make sure to have an alternative API linked                                                                                                                                                                        |
| 7  | `@apiSpec`    | Adds a "API Requirements" heading                                              | A description that applies equally to all valid implementations of the method, including preconditions, postconditions, etc                                                                                        |
| 8  | `@apiNote`    | Adds a "API Note" heading                                                      | A commentary, rationale, or example pertaining to the API                                                                                                                                                          |
| 9  | `@implSpec`   | Adds a "Implementation Requirements" heading                                   | This is where the default implementation (or an overrideable implementation in a class) is specified                                                                                                               |
| 10 | `@implNote`   | Adds a "Implementation Note" heading                                           | This section contains informative notes about the implementation, such as advice to implementors, or performance characteristics that are specific to the implementation in this class of this version of the JDK  |

## 1.2 The summary fragment

Each Javadoc block begins with a brief summary fragment - this is the first sentence up until the character `.` is encountered.

This fragment is very important: it is the only part of the text that appears in certain contexts such as class and method indexes:

```java
/**
 * Returns an Image object that can then be painted on the screen.
 */
```

## 1.3 Code blocks and snippets

`<pre>` is the default HTML tag for preformatted text.
All code blocks and multi-line snippets should be wrapped in `<pre>{@code ...... }</pre>` at minimum.

Code blocks can be optionally formatted and highlighted using [`highlight.js`](https://highlightjs.org/) with the `<code>` tag.
For this, all code blocks and multi-line snippets should be wrapped in `<code class="language-*****"></code>`.

Make sure the correct language is added to the class name: `language-kotlin`, `language-groovy`, or `language-java`:

```java
/**
 * <pre><code class="language-kotlin">
 * project.ext.prop1 = "foo"
 * task doStuff {
 *     ext.prop2 = "bar"
 * }
 * subprojects { ext.${prop3} = false }
 * </code></pre>
 */
```

In order to label the coding language used in the multi-line snippet, you can use additional HTML, `<div class="code-block"><span class="label">Groovy</span>...</div>`:

```java
/**
 * <div class="code-block">
 * <span class="label">Groovy</span>
 * <pre><code class="language-groovy">
 * defaultTasks('some-task')
 * reportsDir = file('reports')
 * </code></pre>
 * </div>
 */
```

To automatically test your code blocks and multi-line snippets, you must add the `autoTested` class to the `<pre>` tag.
Your project must have a test class that extends [`AbstractAutoTestedSamplesTest`](https://github.com/gradle/gradle/blob/master/testing/internal-integ-testing/src/main/groovy/org/gradle/integtests/fixtures/AbstractAutoTestedSamplesTest.groovy) to run them:

```java
/**
 * <pre class='kotlin autoTested'><code class="language-kotlin">
 * project.ext.prop1 = "foo"
 * task doStuff {
 *     ext.prop2 = "bar"
 * }
 * subprojects { ext.${prop3} = false }
 * </code></pre>
 */
```

## 1.4 Where Javadoc is used

At the minimum, Javadoc is present for every public type (including public inner types), and every public or protected member of such a type, with a few exceptions such as overrides and self-explanatory members:

```java
public Image getImage(URL url, String name) {}
```

## 1.5 A note on IDEs

### 1.5.1 IntelliJ IDEA

IntelliJ IDEA will display `<p>` or an empty `*` as a new line:

```java
/**
 * A
 *
 * B
 ```

```java
/**
 * A
 * <p>
 * B
 ```

Render as:

```text
A
B
```

If you want to stop IntelliJ IDEA from auto closing HTML tags:
`Settings -> Editor -> General -> Smart Keys -> "Insert closing tag on tag completion"`

### 1.5.2 Android Studio

Android studio will not display javadoc following a `<p>` so make sure your summary fragment is well detailed:

```java
/**
 * A // Displayed
 * <p>
 * B // Not displayed
 ```
