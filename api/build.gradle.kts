plugins {
    kotlin("jvm")
    `maven-publish`
}

group = "dev.doyu"
version = "1.0"

repositories {
    mavenCentral()
}

// 외부 플러그인이 compileOnly로 받는 모듈이라 Kotlin 표준 라이브러리 말고는 아무것도 끌어오지 않는다.
kotlin {
    jvmToolchain(25)
}

// JitPack이 이 모듈을 `com.github.doyumin.Econet:api:<태그>`로 배포한다.
publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = "api"
            from(components["java"])
        }
    }
}
