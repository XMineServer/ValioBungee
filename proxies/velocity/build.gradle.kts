plugins {
    java
    `maven-publish`
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("xyz.jpenilla.run-velocity") version "2.0.0"
}

dependencies {
    implementation(project(":RedisBungee-Velocity"))
    compileOnly(libs.platform.velocity)
    annotationProcessor(libs.platform.velocity)
    implementation(project(":RedisBungee-Commands"))
    implementation(libs.acf.velocity)

}

description = "RedisBungee Velocity implementation"

java {
    withSourcesJar()
}

tasks {
    runVelocity {
        velocityVersion("3.3.0-SNAPSHOT")
        environment["REDISBUNGEE_PROXY_ID"] = "velocity-1"
        environment["REDISBUNGEE_NETWORK_ID"] = "dev"
    }
    compileJava {
        options.encoding = Charsets.UTF_8.name()
        options.release.set(17)
    }
    processResources {
        filteringCharset = Charsets.UTF_8.name()
    }
    shadowJar {
        relocate("redis.clients.jedis", "com.imaginarycode.minecraft.redisbungee.internal.jedis")
        relocate("redis.clients.util", "com.imaginarycode.minecraft.redisbungee.internal.jedisutil")
        relocate("org.apache.commons.pool", "com.imaginarycode.minecraft.redisbungee.internal.commonspool")
        relocate("com.squareup.okhttp", "com.imaginarycode.minecraft.redisbungee.internal.okhttp")
        relocate("okio", "com.imaginarycode.minecraft.redisbungee.internal.okio")
        relocate("org.json", "com.imaginarycode.minecraft.redisbungee.internal.json")
        relocate("com.github.benmanes.caffeine", "com.imaginarycode.minecraft.redisbungee.internal.caffeine")
        // acf shade
        relocate("co.aikar.commands", "com.imaginarycode.minecraft.redisbungee.internal.acf.commands")
    }

}

// XMine start - публикация shadow-jar в свой Reposilite.
//
// У апстрима этого модуля нет ни `maven-publish`, ни каких-либо repositories:
// он умеет только положить jar в build/libs, а релизы раздаются через GitHub
// Releases. Нам нужен адрес в Maven, потому что образ прокси собирает набор
// плагинов из Reposilite (см. plugins.yml в XMineServer/VelocityServer), а не
// качает файлы с чужих сайтов.
//
// Раздел - кандидат пары форков `fork-snapshot`, а не `third-party`: third-party -
// зеркало чужих jar, а эту сборку правим и собираем мы (вики, ADR-0056). Раздел
// приходит из xmine-publish.yml через XMINE_MAVEN_URL. Версия - адрес сборки, см.
// корневой build.gradle.kts.
//
// Артефакт публикуется БЕЗ классификатора, хотя shadowJar даёт файлу суффикс
// `-all`: имя артефакта несёт платформу (`valiobungee-velocity`), а не способ
// сборки, как у зеркалированного апстримного 0.12.5 в third-party.
publishing {
    publications {
        create<MavenPublication>("xmineFork") {
            groupId = "ru.xmine.thirdparty"
            artifactId = "valiobungee-velocity"
            version = project.version.toString()
            artifact(tasks.named("shadowJar")) {
                classifier = ""
            }
        }
    }
    repositories {
        // Имена свойств учётки - те же, что у остальных проектов XMine
        // (XMinePlugins, Paper, WorldGuard): локально ~/.gradle/gradle.properties,
        // в CI - переменные окружения. НЕ credentials(PasswordCredentials::class):
        // та форма потребовала бы своих xmineUsername/xminePassword и развела бы
        // форк с остальными репозиториями по учёткам.
        maven {
            name = "xmine"
            url = uri(providers.environmentVariable("XMINE_MAVEN_URL")
                .getOrElse("https://maven.xmine.world/fork-snapshot"))
            credentials {
                username = providers.gradleProperty("xmineMavenUsername")
                    .orElse(providers.environmentVariable("XMINE_MAVEN_USERNAME"))
                    .orNull
                password = providers.gradleProperty("xmineMavenPassword")
                    .orElse(providers.environmentVariable("XMINE_MAVEN_PASSWORD"))
                    .orNull
            }
        }
    }
}
// XMine end - публикация shadow-jar в свой Reposilite

// XMine start - публикация только с адресом сборки
// Без -PxmineVersion версия - голый апстримный номер из gradle.properties, и локальный
// `publish` положил бы в раздел-кандидат координату, которая не адрес сборки, а
// координаты там неизменяемы.
val xmineVersion = providers.gradleProperty("xmineVersion")
tasks.withType<PublishToMavenRepository>().configureEach {
    doFirst {
        if (!xmineVersion.isPresent) {
            throw GradleException("Publishing needs -PxmineVersion: the build address computed by .github/workflows/xmine-publish.yml")
        }
    }
}
// XMine end - публикация только с адресом сборки
