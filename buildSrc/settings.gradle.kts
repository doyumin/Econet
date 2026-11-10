// buildSrc는 별도 빌드라 루트의 버전 카탈로그를 직접 불러와야 같은 버전을 쓴다.
dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
