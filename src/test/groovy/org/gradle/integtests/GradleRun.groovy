package org.gradle.integtests

class GradleRun {
    String id
    List execute = []
    String subDir
    Map envs = [:]
    String outputFile
    boolean expectFailure
}