
This spec describes the implementation plan for [GRADLE-1653](http://issues.gradle.org/browse/GRADLE-1653) (a repository notation for
obtaining dependencies from GitHub) and the general approach to providing custom repository notations in the future.

# Use cases

It is becoming common for people to host files on GitHub that Gradle users wish to consume as dependencies. At the moment users need to configure
a custom dependency repository with a custom artifact pattern which is error prone.

The benefit of a custom repository notation is not limited to GitHub and could be applied to other places such as Google Code.

Note: The existing `javascript-base` plugin adds some custom repository notations which should be unified with whatever approach we settle on.

# User visible changes

The ideal API will look very similar to:

    apply plugin: 'github-dependencies'
    
    repositories {
      githubDownloads.maven("«github username»")
      githubDownloads.ivy("«github username»")
    }

That is, an extension named `githubDownloads` is added to the `repositories {}` script block to extend that DSL. It is _not_ a project extension.

The above is equivalent to:

    repositories {
      mavenRepo (name: "«github username's GitHub Downloads", url: "http://cloud.github.com/downloads/«github username»") {
        pattern = "[organisation]/[module]-[revision].[ext]"
      }

      ivy {
        name "«github username's GitHub Downloads"
        url "http://cloud.github.com/downloads/«github username»"
        layout 'pattern', {
          artifact "[organisation]/[module]-[revision].[ext]"
          ivy "[organisation]/[module]-[revision]-ivy(.[ext])"
        }
      }
    }

Note: the GitHub downloads name space is flat for a project. For the project `https://github.com/someuser/someproject`, all download URLs will be a direct child of `https://github.com/downloads/someuser/someproject/`. This is why the ivy files needs the strange pattern.

The obtain the dependency at: `https://github.com/downloads/someuser/someproject/something-1.0.jar` the user will need:

    apply plugin: 'github-dependencies'
    
    repositories {
      githubDownloads.maven("someuser")
    }

    dependencies {
      compile "someproject:something:1.0"
    }

## Sad day cases

We can use a HTTP stand in server to act like GitHub Downloads just like we do with Maven Central, and force it to return 500s, 404s and other fun things.

We should also run 1 happy day case against the real GitHub and one sad day case. We can do this by putting up some downloads for the Gradle project and trying to obtain them. For the sad day case, we can try and get something that we know doesn't exists.

# Integration test coverage

* Resolving dependencies with no metadata
* Resolving dependencies with metadata (ivy and maven) and verifying that transitives are respected
* Handling non existing dependencies
* Verifying dynamic and changing version behaviour (see below)

## Changing dependencies

GitHub does not automatically serve checksums or ETags, which our changing support is predicated on. We will ensure we have an integration test for this situation (i.e. we have no way of telling if it's changed without downloading).

We cannot practically tests a real changing dependency at GitHub.

## Dynamic dependencies

GitHub downloads does not support directory listings. If you hit a directory you get a 403 and some XML. We will have to verify that we can handle this gracefully.

## Headers and other specific behaviour

We should take a capture of real GitHub wire traffic that we base our implementation on (e.g. HTTP headers, directory listing responses) and automatically compare them with the real wire traffic (i.e actually hit GitHub) so that we can be made aware of any behaviour changes by GitHub.
 
# Implementation approach

Effectively:

    class GitHubDependenciesPlugin implements Plugin<Project> {
      void apply(Project project) {
        project.repositories.create("githubDownloads", GitHubDownloadsRepositoryHandlerExtension, project.repositories)
      }
    }
    
    class GitHubDownloadsRepositoryHandlerExtension {
      RepositoryHandler repositories
      GitHubDownloadsRepositoryHandlerExtension(RepositoryHandler repositories) {
        this.repositories = repositories
      }
      
      DependencyResolver maven(String username, Action<DependencyResolver> configure) {
        …
      }
      
      // etc.
    }

# Open issues

* Where does this code live? Do we create a '`github`' subproject?

* How well does it display in the DSL reference?

* Do we want to add support to the class decorator to overload methods like `maven(String username, Action<DependencyResolver> configure)` with `maven(String username, Closure<?> configure)`?
  
