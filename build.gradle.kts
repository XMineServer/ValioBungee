// XMine start - версия сборки
// Версия - адрес сборки <апстрим>-<ветка>-<дата>-<хеш>: её считает из коммита
// .github/workflows/xmine-publish.yml и передаёт через -PxmineVersion (вики, ADR-0056).
// Она же уходит в Constants.VERSION и в дескриптор плагина, так что jar на прокси сам
// говорит, какая это сборка. Без -P остаётся апстримная версия из gradle.properties.
allprojects {
    providers.gradleProperty("xmineVersion").orNull?.let { version = it }
}
// XMine end - версия сборки
