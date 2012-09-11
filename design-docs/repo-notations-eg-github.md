
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
      github.downloads { user "«github username»" }
    }

That is, an extension named `github` is added to the `repositories {}` script block to extend that DSL. It is _not_ a project extension.

It looks like:

    public class GitHubRepositoryHandlerExtension {
        RepositoryHandler repositories
        
        public GitHubDownloadsRepository downloads(Action<GitHubDownloadsRepository> configure) {
          GitHubDownloadsRepository repo = createGitHubDownloadsRepository()
          configure.execute(repo)
          repositories.add(repo)
          return repo
        }
    }
    
The `GitHubDownloadsRepository` looks like:

    public interface GitHubDownloadsRepository extends ArtifactRepository, AuthenticationSupported {
        // For overriding the default base: https://github.com/downloads
        void setBaseUrl(Object baseUrl);
        URI getBaseUrl();
        
        void setUser(String user);
        String getUser();
        
        // Control the artifact pattern, defaults to: "[organisation]/[artifact](-[classifier])(-[revision]).[ext]";
        void setArtifactPattern(String pattern);
        String getArtifactPattern();
        
        // Look for a pom file instead of ivy.xml? defaults true
        void setUsePom(boolean flag);
        boolean isUsePom();
        
        // Control the ivy pattern (ignored if !isUsePom()), defaults to: "[organisation]/[artifact](-[revision])-ivy.xml";
        void setIvyPattern(String pattern);
        String getIvyPattern();
    }

Given:
    
    repositories {
      github.downloads { user "githubUser" }
    }

this is how different notations will resolve:

* `myProject:myThing` - `https://github.com/downloads/githubUser/myProject/myThing.jar` (`githubUser/myProject/myThing.pom`)
* `myProject:myThing:1.0` - `https://github.com/downloads/githubUser/myProject/myThing-1.0.jar` (`githubUser/myProject/myThing-1.0.pom`)
* `myProject:myThing:1.0@zip` - `https://github.com/downloads/githubUser/myProject/myThing-1.0.zip` (`githubUser/myProject/myThing-1.0.pom`)

Given:
    
    repositories {
      github.downloads { user "githubUser"; usePoms false }
    }

this is how different notations will resolve:

* `myProject:myThing` - `https://github.com/downloads/githubUser/myProject/myThing.jar` (`githubUser/myProject/myThing-ivy.xml`)
* `myProject:myThing:1.0` - `https://github.com/downloads/githubUser/myProject/myThing-1.0.jar` (`githubUser/myProject/myThing-1.0-ivy.xml`)
* `myProject:myThing:1.0@zip` - `https://github.com/downloads/githubUser/myProject/myThing-1.0.zip` (`githubUser/myProject/myThing-1.0-ivy.xml`)

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

The implementation of `GitHubDownloadsRepository`, implements `ArtifactRepositoryInternal`…

    public class DefaultGitHubDownloadsRepository implements GitHubDownloadsRepository, ArtifactRepositoryInternal {
      public DependencyResolver createResolver() {
        // create a resolver based on the user's config
      }
    }
    
If `isUsePoms() == true` a `MavenResolver` will be returned, otherwise an `IvyResolver`.

# Open issues

* Private GitHub repos require preemptive auth for download request, and 404s are returned for any kind of authn/authz failure. Not sure how our credential support will hanlde this.

* If the user wants to use a POM file they can't use a group with `.`'s in it, because the maven resolver converts `.` to `/` when resolving the pattern. The GitHub downloads space is flat so this won't work. Ideally, we'd have a resolver that can deal with POMs that doesn't require `m2Compatible = true`.
