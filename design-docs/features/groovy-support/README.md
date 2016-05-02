# Groovy support

## Groovydoc

### Supporting noTimestamp and noVersionStamp flags

Groovy 2.4.6 adds two new flags to its Groovydoc command. Using these flags produce Groovydoc HTML
pages without timestamp or Groovy version stamp information embedded within them. This means that
it is harder to determine when or which version of Groovy produced the pages but means the pages
remain totally unchanged across builds unless the actual content changes.

This spec adds support for using these flags with Gradle's Groovy plugin.
Gradle's Groovy plugin automatically provides Groovydoc support and allows configuration
by setting properties. Two properties are added and can be used as follows.

    groovydoc {
        noTimestamp = true
        noVersionStamp = true
    }

The flags are ignored for versions of Groovy prior to 2.4.6.
