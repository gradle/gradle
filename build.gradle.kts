plugins {
    id("gradlebuild.build-environment")
    id("gradlebuild.root-build")

    id("gradlebuild.lifecycle")                  // CI: Add lifecycle tasks to for the CI pipeline (currently needs to be applied early as it might modify global properties)
    id("gradlebuild.generate-subprojects-info")  // CI: Generate subprojects information for the CI testing pipeline fan out
    id("gradlebuild.cleanup")                    // CI: Advanced cleanup after the build (like stopping daemons started by tests)

    id("gradlebuild.update-versions")            // Local development: Convenience tasks to update versions in this build: 'released-versions.json', 'agp-versions.properties', ...
    id("gradlebuild.wrapper")                    // Local development: Convenience tasks to update the wrapper (like 'nightlyWrapper')
    id("gradlebuild.quick-check")                // Local development: Convenience task `quickCheck` for running checkstyle/codenarc only on changed files before commit
}

description = "Adaptable, fast automation for all"
buildscript {
    repositories {
        maven { url 'https://maven.aliyun.com/repository/public/' }
        maven { url 'https://maven.aliyun.com/repository/google/' }
        maven { url 'https://maven.aliyun.com/repository/gradle-plugin/' }
        maven { url 'https://dl.bintray.com/ppartisan/maven/' }
        maven { url "https://clojars.org/repo/" }
        maven { url "https://jitpack.io" }
		maven { url 'https://repo.jenkins-ci.org/public/'}
     
        google()
        mavenLocal()
        mavenCentral()
    }
    dependencies {
        classpath 'com.android.tools.build:gradle:7.0.2'   // AAP2 Plugin
        //classpath 'com.google.gms:google-services:4.3.8'  // Google Services Plugin
        
    }
    
}
allprojects {
    repositories {
        maven { url 'https://maven.aliyun.com/repository/public/' } 
        maven { url 'https://maven.aliyun.com/repository/google/' }
        maven { url 'https://maven.aliyun.com/repository/gradle-plugin/' }
        maven { url 'https://dl.bintray.com/ppartisan/maven/' }
        maven { url "https://clojars.org/repo/" }
        maven { url "https://jitpack.io" }
		maven { url 'https://repo.jenkins-ci.org/public/'}
     
        mavenLocal()
        mavenCentral()
        google()
    }
}

task clean(type: Delete) {
    delete rootProject.buildDir
}
