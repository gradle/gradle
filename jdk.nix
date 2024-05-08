{ pkgs ? import <nixpkgs> {}, stdenv ? pkgs.stdenv }:

stdenv.mkDerivation {
  name = "jdk_11_0_23";
  src = pkgs.fetchurl {
    url = "https://api.adoptium.net/v3/binary/version/jdk-11.0.23+9/linux/x64/jdk/hotspot/normal/adoptium";
    sha256 = "1hhp6jyvazic92dpqx9hcpvxrsxd0bcmy6111wjf6nq1lfkpxr13";
    name = "adoptium-jdk-11.0.23.tar.gz";
  };

  installPhase = ''
    mkdir -p $out
    tar -xzf $src --strip-components=1 -C $out
  '';

  meta = {
    description = "OpenJDK 11.0.23 from Adoptium";
    homepage = "https://adoptium.net/";
  };
}


