package com.example.myproduct.user.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.myproduct.model.MyProductRelease;
import com.example.myproduct.model.MyProductReleaseList;

import java.net.URL;
import java.util.Arrays;
import java.util.stream.Collectors;

public class DataRetriever {

    static public class Version {
        public String version;
        public String buildTime;
        public boolean current;
        public boolean snapshot;
        public boolean nightly;
        public boolean releaseNightly;
        public boolean activeRc;
        public String rcFor;
        public String milestoneFor;
        public boolean broken;
        public String downloadUrl;
        public String checksumUrl;
        public String wrapperChecksumUrl;
    }

    public static MyProductReleaseList retrieve() {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            URL url = new URL("https://services.gradle.org/versions/all");
            Version[] versions = objectMapper.readValue(url, Version[].class);
            return new MyProductReleaseList(Arrays.stream(versions).map(r -> new MyProductRelease(
                r.version, releaseNotesURL(r.version))).collect(Collectors.toList()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String releaseNotesURL(String version) {
        if (version.contains("+")) {
            version = "nightly";
        }
        return "https://docs.gradle.org/" + version + "/release-notes.html";
    }
}
