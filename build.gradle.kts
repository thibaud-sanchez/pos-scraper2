plugins {
    kotlin("jvm") version "2.0.10"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
    implementation("net.sourceforge.htmlunit:htmlunit:2.52.0")
    implementation("org.seleniumhq.selenium:selenium-firefox-driver:3.+")
    implementation("org.seleniumhq.selenium:selenium-chrome-driver:3.+")
    implementation("org.seleniumhq.selenium:selenium-java:3.+")

    implementation("com.squareup.okhttp3:okhttp:4.9.3")

    implementation("org.json:json:20200518")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-json-org:2.11.0")
    implementation("net.lightbody.bmp:browsermob-core:2.1.5")
    implementation ("org.apache.commons:commons-csv:1.5")
    implementation("org.ktorm:ktorm-core:3.4.1")
    implementation("org.xerial:sqlite-jdbc:3.18.0")

}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}