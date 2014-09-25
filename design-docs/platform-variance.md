# Intro
This doc is purely speculative. Its purpose is to discuss our overall vision going forward with variants/platform support.
It was written the 16th September 2014, and might be outdated or plain wrong, in which case it should be removed.

# Platform variance alternatives
The concept of variance is one of the key features on our roadmap (http://www.gradle.org/roadmap#variant).
The highlevel goal is to find a flexible and safe way for users to describe what they want their build to do.
From this we can infer what needs to be done to get there.

There are a bunch of considerations to take:

- we need an easy way to define new platforms. Examples: Scala, Android, Play, .... who knows what other...
- we need to a way to easily extend/combine existing platforms: Scala is a Jvm platform with some additional properties, Android is a combination of Jvm and C (NDK) and some additional properties, Gradle is just Groovy with some extra APIs, ...

Below are some alternative strategies. I am leaning towards Alt 1, but I am still trying to piece together how things work in general so I might be wrong. It is also possible to mix the approaches.

## Alternative 1: PlatformVariants
In this alternative the general idea is to split the concept of a "Platform" into smaller pieces, Platform variants, which can be combined using properties from different Platforms. As an example, you could have a platform variant such as Scala which also mixes in Android properties. Each variant would result in one set of binaries (i.e. a "release").

Model breakdown:
- Each (language) plugin defines PlatformProperties .
- A user may define multiple PlatformVariants, based on the available PlatformProperties (defined in the plugins).
- Each plugin registers toolchains that knows how to build PlatformProperies.
- For each variant a new composite toolchains is created.
- The compoisite toolchains are responsible for knowing how to build a PlatformVariant. If there PlatformVariants which has properties that cannot be built by any toolchains, or are incompatible, the build throws an error (by definitions). If there are multiple toolchains that can build a variant we could have a warning (or maybe this is Ok - TBD). 
- The PlatformPlugin (or VariantPlugin) creates compile tasks based on the toolchains, composite toolchains and binaryspecs registered.

Pros:

- Free to mix multiple platforms together
- Customization is easy

Cons:

- Defining defaults is harder: what is the default when 2 plugins are loaded? Means we might force users to define more spesifically their chosen variant.
- No type level validation on what works together, i.e. ?
- Possibly hard to define exactly which platforms works together (less stable)?
- Not sure it is possible to elegantly

### Example (again, only to give a better sense of how it _could_ work) snippets:
```java
//PlatformPlugin.java

@RuleSource
static class Rules {
    @Model
    PlatformContainer createPlatforms(ServiceRegistry serviceRegistry) {
      //add a platform container, which contains PlatformVariants
    }
    
    @Model
    void createBinaries(BinaryContainer binarySpecs, ToolChainRegistryInternal toolChains, ...) {
      //based on toolChains and binarySpecs created in each plugin
    }
    
    @Mutate
    void createCompileTasks(TaskContainer tasks, BinaryContainer binaries) {
      //add tasks to each binary container
    }
}

//ScalaLanguagePlugin.java

@RuleSource
static class Rules {
   @Model
   public ScalaPropertiesContainer createScalaProperties(PlatformContainer platforms) {
      //parse platform container create scala properties
   }
   
   @Mutate
   public void createScalaToolChains(ToolChainRegistryInternal toolChains, BinaryContainer binarySpecs, ScalaPlatformContainer scalaPlatforms) {
     //create create and apply each scalaPlatform to each toolChain
   }
   
   //setup sources as well if needed
}

//ScalaToolChain.java
public Set<Platform> select(Set<Platform> platforms) {
    //
}
```

### Example dsl (please ignore syntax here - the important thing is the model. Example only provided as a reference to better explain.):
```groovy
    apply plugin: 'scala-lang'
    apply plugin: 'android'

    libraries {
        myLib {
            
        }
    }
    
    sources {
        myLib {
            scala.source.srcDir "src/main/scala"
        }
    }
    
    platforms {
        myLib { //or myLib then platforms - does not matter at this point
          variants [variant {
              jvm {
                targetCompatibility "1.7"
              }
              scala {
                  binaryVersion "2.10"
                  version "2.10.4"
              }
              android {
                  compileSdkVersion 19
                  ndkRevision "10b"
              }
            }, variant {
              // no jvm, use default
              scala {
                  binaryVersion "2.11"
                  version "2.11.2"
              }
              android {
                  compileSdkVersion 19
                  ndkRevision "10b"
              } //if I tried to have rust {} here Gradle should tell me that there is no toolChain that can build scala, android and rust
            }]
        }
    }
```

## Alternative 2: Language defines a platform which can be spesialized
Each language plugin sets up its own rules and is responsible for their own platform. Similar to how it looks today, though with a more consistent architecture (i.e. we use the same structures to do everything). 

Model implementation ideas:
- Platforms are extended through inheritence to the Platform interface: ScalaPlatform extends JavaPlatform, JavaPlatform extends JvmPlatform, JvmPlatform extends Platform (already done)
- Each plugin defines their own Platform and variances.  (partially done)
- Each plugin sets up rules and extensions or a PlatformContainer. There could be a PlatformRules that is inherited to stay DRY. 
- Each plugin defines their binary spec and creates their associated compile tasks.

Pros:

- Possible to statically check what works together or not.
- Easier to be specific about what works together.
- Only requires that the current plugins have a consistent approach (almost finished).

Cons:

- Harder to customise and mix together variants.
- More code as we go along.
- No "enforced" consistency, risk degenerating because it is just as easy to create something completely different than to reuse the exisiting code.

### Example dsl (ignore syntax - the important thing is the model):
```
    apply plugin: 'scala-lang'
    apply plugin: 'android'

    jvm {
      libraries {
        myLib {
          target "1.7" //jvm
          scala {
            binaryVersion "2.10"
            version "2.10.4"
          }
          android {
            compileSdkVersion 19
            ndkRevision "10b"
          }
        }
      }
    }
```
