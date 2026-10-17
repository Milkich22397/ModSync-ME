package com.bleudev.modsync.datagen.provider

import net.fabricmc.fabric.api.datagen.v1.FabricPackOutput
import net.fabricmc.fabric.api.datagen.v1.provider.FabricLanguageProvider
import net.minecraft.core.HolderLookup
import java.util.concurrent.CompletableFuture

class ModSyncDefaultLanguageProvider(
    packOutput: FabricPackOutput, registryLookup: CompletableFuture<HolderLookup.Provider>
) : FabricLanguageProvider(packOutput, registryLookup) {
    override fun generateTranslations(
        registryLookup: HolderLookup.Provider,
        builder: TranslationBuilder
    ) {
        builder.add("modsync.update.confirm.title", "Confirm installation")
        builder.add("modsync.update.confirm.message", "These mods will be updated.\nAre you sure you want to install them?")
        builder.add("modsync.update.confirm.yes", "Confirm, join the server")
        builder.add("modsync.update.confirm.no", "No, do not join the server")
        builder.add("modsync.update.wait.running", "Updating mods")
        builder.add("modsync.update.wait.running.wait", "Please wait")
        builder.add("modsync.update.end.restart", "Restart Minecraft")
        builder.add("modsync.update.end.restart.more", "Close Minecraft to install the downloaded mods. Wait a few seconds before launching it again.")
        builder.add("modsync.update.error.title", "Mod update failed")
        builder.add("modsync.update.error.message", "Could not update mods from %s.\n%s\nCheck the ModSync HTTP port and logs/latest.log. Installed mods were not changed.")
        builder.add("modsync.update.end.restart.yes", "Restart")
        builder.add("modsync.update.end.restart.no", "Later")
    }
}

class ModSyncRuLanguageProvider(
    packOutput: FabricPackOutput, registryLookup: CompletableFuture<HolderLookup.Provider>
) : FabricLanguageProvider(packOutput, "ru_ru", registryLookup) {
    override fun generateTranslations(
        registryLookup: HolderLookup.Provider,
        builder: TranslationBuilder
    ) {
        builder.add("modsync.update.confirm.title", "Подтвердите установку")
        builder.add("modsync.update.confirm.message", "Эти моды будут обновлены\nВы точно хотите их установить?")
        builder.add("modsync.update.confirm.yes", "Подтвердить, зайти на сервер")
        builder.add("modsync.update.confirm.no", "Нет, не заходить на сервер")
        builder.add("modsync.update.wait.running", "Обновление модов")
        builder.add("modsync.update.wait.running.wait", "Пожалуйста подождите")
        builder.add("modsync.update.end.restart", "Перезапустите Minecraft")
        builder.add("modsync.update.end.restart.more", "Закройте Minecraft для установки скачанных модов. Подождите несколько секунд перед повторным запуском.")
        builder.add("modsync.update.error.title", "Не удалось обновить моды")
        builder.add("modsync.update.error.message", "Не удалось обновить моды с %s.\n%s\nПроверьте HTTP-порт ModSync и logs/latest.log. Установленные моды не изменены.")
        builder.add("modsync.update.end.restart.yes", "Перезапустить")
        builder.add("modsync.update.end.restart.no", "Позже")
    }
}
