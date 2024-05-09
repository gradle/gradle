{ pkgs ? import <nixpkgs> {}, stdenv ? pkgs.stdenv }:


let
  jdk11Url = "https://raw.githubusercontent.com/gradle/gradle/f93a3dc769fbcdafbf75cd5f251f546baf05ff47/jdk.nix";

  jdk11Package = import (pkgs.fetchurl {
    url = jdk11Url;
    sha256 = "LWuyEZPYlPpAE+5l29uuxnKupmJtpJj0kzh5wGiEH2g=";
  }) {};
in
stdenv.mkDerivation {
  name = "JDK_11_0_23";
  buildInputs = [ jdk11Package ];

  shellHook = ''
    export JDK11_PATH="${jdk11Package}"
    export JAVA_HOME="${jdk11Package}"
    echo "##teamcity[setParameter name='env.JAVA_HOME' value='${jdk11Package}']"
    echo "##teamcity[setParameter name='linux.java11.openjdk.64bit' value='${jdk11Package}']"
    echo "##teamcity[setParameter name='linux.java11.adoptiumopenjdk.64bit' value='${jdk11Package}']"
  '';
}
