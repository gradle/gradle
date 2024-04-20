{
  "project_info": {
    "project_number": "845087703750",
    "project_id": "my-proyect-rm",
    "storage_bucket": "my-proyect-rm.appspot.com"
  },
  "client": [
    {
      "client_info": {
        "mobilesdk_app_id": "1:845087703750:android:6d5d45d6060f9733b7cdc7",
        "android_client_info": {
          "package_name": "com.example.rmsports1902"
        }
      },
      "oauth_client": [
        {
          "client_id": "845087703750-c3ok2k210c9g0fhng0jso3udus7ipdpg.apps.googleusercontent.com",
          "client_type": 3
        }
      ],
      "api_key": [
        {
          "current_key": "AIzaSyD-nUG-jIA_IPtWvmGEVyRmdjgXlNceZyM"
        }
      ],
      "services": {
        "appinvite_service": {
          "other_platform_oauth_client": [
            {
              "client_id": "845087703750-c3ok2k210c9g0fhng0jso3udus7ipdpg.apps.googleusercontent.com",
              "client_type": 3
            }
          ]
        }
      }
    }
  ],
  "configuration_version": "1"
}
plugins {
    id("gradlebuild.build-environment")
    id("gradlebuild.root-build")

    id("gradlebuild.teamcity-import-test-data")  // CI: Import Test tasks' JUnit XML if they're UP-TO-DATE or FROM-CACHE
    id("gradlebuild.lifecycle")                  // CI: Add lifecycle tasks to for the CI pipeline (currently needs to be applied early as it might modify global properties)
    id("gradlebuild.generate-subprojects-info")  // CI: Generate subprojects information for the CI testing pipeline fan out
    id("gradlebuild.cleanup")                    // CI: Advanced cleanup after the build (like stopping daemons started by tests)

    id("gradlebuild.update-versions")            // Local development: Convenience tasks to update versions in this build: 'released-versions.json', 'agp-versions.properties', ...
    id("gradlebuild.wrapper")                    // Local development: Convenience tasks to update the wrapper (like 'nightlyWrapper')
}

description = "Adaptable, fast automation for all"

dependencyAnalysis {
    issues {
        all {
            ignoreSourceSet("archTest", "crossVersionTest", "docsTest", "integTest", "jmh", "peformanceTest", "smokeTest", "testInterceptors", "testFixtures", "smokeIdeTest")
        }
    }
}
