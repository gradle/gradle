This spec describes the implementation plan for [GRADLE-1653](http://issues.gradle.org/browse/GRADLE-1653) (a repository notation for
obtaining dependencies from GitHub) and the general approach to providing custom repository notations in the future.

# Use cases

It is becoming common for people to host files on GitHub that Gradle users wish to consume as dependencies. At the moment users need to configure
a custom dependency repository with a custom artifact pattern which is error prone. 

The benefit of a custom repository notation is not limited to GitHub and could be applied to other places such as Google Code.

# User visible changes

The ideal API will look very similar to:

    apply plugin: 'github-dependencies'
    
    repositories {
      github.downloads("«github username"», "«github repository»")
      github.downloads("«github username"», "«github repository»") { 
        // further configuration 
      }
    }

That is, an extension named `github` is added to the `repositories {}` script block to extend that DSL. It is _not_ a project extension.

It looks like:

    class GitHubRepositoryHandlerExtension {
      GitHubDownloadsRepository downloads(String user, String repo) {}
      GitHubDownloadsRepository downloads(String user, String repo, Action<GitHubDownloadsRepository> configure) {}
    }
    
The `GitHubDownloadsRepository` looks like:

    interface GitHubDownloadsRepository extends ArtifactRepository {
        // For overriding the default base: https://github.com/downloads
        void setBaseUrl(Object baseUrl)
        URI getBaseUrl()
        
        void setUser(String user)
        String getUser()

        void setRepo(String repo)
        String getRepo()
    }

Given:
    
    repositories {
      github.downloads("githubUser", "myProject")
    }

This is how different notations will resolve:

* `org.my:myThing` - `https://github.com/downloads/githubUser/myProject/org.my-myThing.jar`
* `org.my:myThing:1.0` - `https://github.com/downloads/githubUser/myProject/org.my-myThing-1.0.jar`
* `org.my:myThing:1.0@zip` - `https://github.com/downloads/githubUser/myProject/org.my-myThing-1.0.zip`
* `:myThing` - `https://github.com/downloads/githubUser/myProject/myThing.jar`
* `:myThing:1.0` - `https://github.com/downloads/githubUser/myProject/myThing.jar`

Notes:

1. Dependency metadata is not supported in this initial implementation
2. Private github repositories are unsupported

## Sad day cases

We can use a HTTP stand in server to act like GitHub Downloads just like we do with Maven Central, and force it to return 500s, 404s and other fun things.

We should also run 1 happy day case against the real GitHub and one sad day case. We can do this by putting up some downloads for the Gradle project and trying to obtain them. For the sad day case, we can try and get something that we know doesn't exists.

# Integration test coverage

* Resolving dependencies with no metadata
* Resolving dependencies with metadata (ivy and maven) and verifying that transitives are respected
* Handling non existing dependencies
* Verifying dynamic and changing version behaviour (see below)

## Changing dependencies

It is unlikely that checksums will be published @ GitHub downloads. However, GitHub downloads does serve last modified and ETags.

Changing dependencies will be supported.

## Dynamic dependencies

GitHub downloads does not support directory listings. If you hit a directory you get a 403 and some XML. We will have to verify that we can handle this gracefully.

Dynamic dependencies will not be supported.

## Headers and other specific behaviour

We should take a capture of real GitHub wire traffic that we base our implementation on (e.g. HTTP headers, directory listing responses) and automatically compare them with the real wire traffic (i.e actually hit GitHub) so that we can be made aware of any behaviour changes by GitHub.
 
# Implementation approach

The implementation of `GitHubDownloadsRepository`, implements `ArtifactRepositoryInternal`…

    public class DefaultGitHubDownloadsRepository implements GitHubDownloadsRepository, ArtifactRepositoryInternal {
      public DependencyResolver createResolver() {
        // create a resolver based on the user's config
      }
    }
    
If `isUsePoms() == true` a `MavenResolver` will be returned, otherwise an `IvyResolver`.

# Open issues

* Private GitHub repos require preemptive auth for download request, and 404s are returned for any kind of authn/authz failure. Not sure how our credential support will hanlde this.

* Support for metadata will be needed in the future. This should be a configurable aspect of the `GitHubDownloadsRepository` DSL.
