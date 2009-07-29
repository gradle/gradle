package org.gradle.integtests

class GradleRun {
    String id
    List execute = []
    String file
    String subDir
    Map envs = [:]
    String outputFile
    boolean expectFailure
}