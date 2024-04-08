# Gradle Javadoc Style Guide

## 1.1 Formatting

### 1.1.1 General form

The basic formatting of Javadoc blocks is as seen in this example:

```java
/**
 * Returns an Image object that can then be painted on the screen.
 * The url argument must specify an absolute <a href="#{@link}">{@link URL}</a>. The name
 * argument is a specifier that is relative to the url argument.
 * <p>
 * This method always returns immediately, whether or not the
 * image exists. When this applet attempts to draw the image on
 * the screen, the data will be loaded. The graphics primitives
 * that draw the image will incrementally paint on the screen.
 *
 * @param  url  an absolute URL giving the base location of the image
 * @param  name the location of the image, relative to the url argument
 * @return      the image at the specified URL
 * @see         Image
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
- Notice the inline tag `{@link URL}`, which converts to an HTML hyperlink pointing to the documentation for the URL class.
- If you have more than one paragraph in the doc comment, separate the paragraphs with a `<p>` paragraph tag, as shown.
- Insert a blank comment line between the description and the list of tags, as shown.
- The first line that begins with an `@` character ends the description; you cannot continue the description following block tags.
- Block tags must be added in order.
- The last line contains the end-comment delimiter ( `*/`).

So lines won't wrap, limit any doc-comment lines to 80 characters.

### 1.1.2 HTML

Javadoc permits only a subset of HTML tags: `"a", "abbr", "acronym", "address", "area", "b", "bdo", "big", "blockquote", "br", "caption", "cite", "code", "colgroup", "dd", "del", "dfn", "div", "dl", "dt", "em", "fieldset", "font", "h1", "h2", "h3", "h4", "h5", "h6", "hr", "i", "img", "ins", "kbd", "li", "ol", "p", "pre", "q", "samp", "small", "span", "strong", "sub", "sup", "table", "tbody", "td", "tfoot", "th", "thead", "tr", "tt", "u", "ul", "var"`.

Attempt to limit your usage of HTML to: `"h1", "h2", "h3", "h4", "em", "li", "ol", "p", "ul"`.

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

Each paragraph except the first has `<p>` immediately before the first word, with no space after it:

```java
/**
 * argument is a specifier that is relative to the url argument.
 * <p>This method always returns immediately, whether or not the
 */
```

Alternatively, a `<p>` can be placed on a separate line:

```java
/**
 * argument is a specifier that is relative to the url argument.
 * <p>
 * This method always returns immediately, whether or not the
 */
```

The inline tag `{@link URL}`, which converts to an HTML hyperlink pointing to the documentation for the URL class. 
This inline tag can be used anywhere that a comment can be written, such as in the text following block tags:

```java
/**
 * The url argument must specify an absolute <a href="#{@link}">{@link URL}</a>. The name
 */
```

HTML tags for other block-level elements, such as `<ul>` or `<table>`, are not preceded with `<p>`.

### 1.1.4 Symbols

Entities for the less than symbol (<) and the greater than symbol (>) should be written as `&lt`; and `&gt;`. 
Similarly, the ampersand (&) should be written as `&amp;`.

```java
/**
 * The url argument must specify an absolute <a href="#{@link}">{@link URL}</a>. The name
 */
```
### 1.1.5 Links

The `{@link <class or method reference>}` tag is specifically used to link to the Javadoc of other classes and methods. 
This is an inline tag that converts to an HTML hyperlink pointing to the documentation of the given class or method reference

```java
/**
 * This &amp; that.
 */
```

### 1.1.6 Code keywords, names, and variables

Use `<code>...</code>` style for keywords and names including:
- Java keywords
- package names
- class names
- method names
- interface names
- field names
- argument names
- code examples (see section 1.3 below)

```java
/**
 * Use the <code>Project</code> instance to configure the project.
 */
```

### 1.1.7 Block tags

Any of the standard "block tags" that are used appear in the order `@param`, `@return`, `@throw`s`, `@deprecated`, and these four types never appear with an empty description:

```java
/**
 * @param  url  an absolute URL giving the base location of the image
 * @param  name the location of the image, relative to the url argument
 * @return      the image at the specified URL
 * @see         Image
 */
```

Full list of tags (in order):

| # | Tag           | Usage                                                                          | Notes                                                                                      |
|---|---------------|--------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------|
| 1 | `@param`      | Methods and constructors only                                                  | Include this if applicable                                                                 |
| 2 | `@return`     | Methods only                                                                   | Include this if applicable                                                                 |
| 3 | `@exception`  | Same as `@throws`                                                              | Include this if applicable                                                                 |
| 4 | `@see`        | Adds a “See Also” heading with a link or text entry that points to a reference | `@see string`<br>`@see <a href=”URL#value”>label</a>`<br>`@see package.class#member label` |
| 5 | `@since`      | Adds a “Since” heading                                                         | Include the Gradle version if applicable                                                   |
| 6 | `@deprecated` | Adds a comment indicating that this API should no longer be used               | Make sure to have an alternative API linked                                                |

Custom Gradle tags:

| # | Tag            | Usage                                        | Notes                                            |
|---|----------------|----------------------------------------------|--------------------------------------------------|
| 1 | `@apiNote`     | Adds a “API Note” heading                    | Describe interesting details about the API       |
| 2 | `@implSpec`    | Adds a “Implementation Requirements” heading | Describe what we expect the plugin author to do  |
| 3 | `@implNote`    | Adds a “Implementation Note” heading         | Describe the implementation provided by Gradle   |

When a block tag doesn't fit on a single line, continuation lines are indented four (or more) spaces from the position of the `@`.

## 1.2 The summary fragment

Each Javadoc block begins with a brief summary fragment. 
This fragment is very important: it is the only part of the text that appears in certain contexts such as class and method indexes:

```java
/**
 * Returns an Image object that can then be painted on the screen.
 */
```

## 1.3 Code blocks and snippets

`<pre>` is the default HTML tag for preformatted text.
All code blocks and snippets should be wrapped in `<pre>...</pre>` at minimum.

Code blocks are formatted using `highlight.js` using the `<code>` tag.
All code blocks and snippets should be wrapped in `<code class="language-*****"></code>` to be automatically highlighted.
Make sure the correct language is added to the class name: `language-kotlin`, `language-groovy`, or `language-java`.

A formatted and highlighted code snippet looks should look as follows:

```java
/**
 * <pre class='groovy autoTested'><code class="language-kotlin">
 * project.ext.prop1 = "foo"
 * task doStuff {
 *     ext.prop2 = "bar"
 * }
 * subprojects { ext.${prop3} = false }
 * </code></pre>
 */
```

In order to label the coding language used in the snippet, you can use additional HTML, `<div class="code-block"><span class="label">Groovy</span>...</div>`:

```java
/**
 * <div class="code-block">
 * <span class="label">Groovy</span>
 * <pre class='groovy autoTested'><code class="language-groovy">
 * defaultTasks('some-task')    // Delegates to Project.defaultTasks()
 * reportsDir = file('reports') // Delegates to Project.file() and the Java Plugin
 * </code></pre>
 * </div>
 */
```

## 1.4 Where Javadoc is used

At the minimum, Javadoc is present for every public class, and every public or protected member of such a class, with a few exceptions such as overrides and self-explanatory members:

```java
public Image getImage(URL url, String name) {}
```
