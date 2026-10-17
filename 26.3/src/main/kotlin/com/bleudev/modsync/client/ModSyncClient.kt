package com.bleudev.modsync.client

import com.bleudev.modsync.client.custom.gui.screens.ModsUpdatingScreen
import com.bleudev.modsync.LOGGER_GENERAL
import com.bleudev.modsync.client.util.ModSyncer
import com.bleudev.modsync.client.util.cancel
import com.bleudev.modsync.client.util.closeScreen
import com.bleudev.modsync.custom.packet.payload.ModSyncInfo
import com.bleudev.modsync.http.serverbound.serialization.ModSyncMetadata
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ConfirmScreen
import net.minecraft.client.gui.screens.ConnectScreen
import net.minecraft.client.gui.screens.DisconnectedScreen
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.network.chat.Component

class ModSyncClient : ClientModInitializer {
    private val requireRestartAddresses = arrayListOf<String>()

    private var updateAddress: String = ""
    private var toUpdate: List<ModSyncMetadata.ModMetadata> = listOf()
    private var updating: Boolean = false
    private var resultAttempt: Long = -1

    override fun onInitializeClient() {
        ClientConfigurationNetworking.registerGlobalReceiver(ModSyncInfo.TYPE) { payload, _ ->
            val host = ClientTempStorageManager.getInstance().serverHost ?: return@registerGlobalReceiver
            val state = ClientTempStorageManager.getInstance()
            val attempt = state.connectionAttempt
            val address = java.net.URI("http", null, host, payload.port, null, null, null).toString()
            Thread({
                try {
                    val mods = ModSyncer.getInstance().fetch(address)
                    Minecraft.getInstance().execute {
                        if (state.connectionAttempt == attempt && !updating) {
                            updateAddress = address
                            toUpdate = mods
                            resultAttempt = attempt
                        }
                    }
                } catch (error: Exception) {
                    LOGGER_GENERAL.error("Could not fetch ModSync metadata from $address", error)
                    Minecraft.getInstance().execute {
                        if (state.connectionAttempt == attempt) showUpdateError(address, error)
                    }
                }
            }, "ModSync metadata").apply { isDaemon = true; start() }
        }
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            val already = ClientTempStorageManager.getInstance().serverAddress in requireRestartAddresses
            if (!updating) {
                if (resultAttempt == ClientTempStorageManager.getInstance().connectionAttempt && updateAddress.isNotEmpty() && toUpdate.isNotEmpty() && !already) {
                    updating = true
                    mc.ensureCancelConnect()
                    mc.disconnect(confirmUpdateScreen(), true)
                }
                if (mc.gui.screen() is ConnectScreen && already) {
                    mc.ensureCancelConnect()
                    mc.showRestartScreen()
                }
            }
        }
    }

    private fun Minecraft.ensureCancelConnect() {
        (this.gui.screen() as? ConnectScreen)?.cancel()
    }

    private fun confirmUpdateScreen(): ConfirmScreen {
        val message = Component.translatable("modsync.update.confirm.message")
        for ((id, version) in toUpdate) {
            val container = FabricLoader.getInstance().getModContainer(id)

            if (container.isPresent) {
                val current = container.get().metadata.version.friendlyString
                message.append(Component.literal("\n$id: $current -> $version").withStyle(ChatFormatting.GREEN))
            } else {
                message.append(Component.literal("\n$id: $version").withStyle(ChatFormatting.AQUA))
            }
        }
        return ConfirmScreen(
            {
                val mc = Minecraft.getInstance()
                if (it) {
                    val already = ClientTempStorageManager.getInstance().serverAddress in requireRestartAddresses
                    if (updateAddress.isNotEmpty() && toUpdate.isNotEmpty() && !already) {
                        val address = updateAddress
                        val mods = toUpdate.toList()
                        val serverAddress = ClientTempStorageManager.getInstance().serverAddress
                        mc.gui.setScreen(updatingScreen())
                        Thread({
                            try {
                                ModSyncer.getInstance().syncAll(address, mods)
                                mc.execute {
                                    serverAddress?.let { a -> requireRestartAddresses.add(a) }
                                    toUpdate = listOf()
                                    updateAddress = ""
                                    updating = false
                                    mc.showRestartScreen()
                                }
                            } catch (error: Exception) {
                                LOGGER_GENERAL.error("ModSync update failed", error)
                                mc.execute { showUpdateError(address, error) }
                            }
                        }, "ModSync download").apply { isDaemon = true; start() }
                    } else {
                        updating = false
                        mc.closeScreen()
                    }
                } else {
                    toUpdate = listOf()
                    updateAddress = ""
                    updating = false
                    mc.closeScreen()
                }
            },
            Component.translatable("modsync.update.confirm.title"),
            message,
            Component.translatable("modsync.update.confirm.yes"),
            Component.translatable("modsync.update.confirm.no")
        )
    }

    private fun updatingScreen() = ModsUpdatingScreen()

    private fun showUpdateError(address: String, error: Exception) {
        toUpdate = listOf()
        updateAddress = ""
        updating = false
        val mc = Minecraft.getInstance()
        mc.ensureCancelConnect()
        mc.disconnect(DisconnectedScreen(TitleScreen(),
            Component.translatable("modsync.update.error.title"),
            Component.translatable("modsync.update.error.message", address, error.message ?: error.javaClass.simpleName)), true)
    }

    private fun Minecraft.showRestartScreen() {
        this.gui.setScreen(ConfirmScreen(
            {
                if (it) {
                    // Let the main loop exit and perform normal shutdown before replacing jars.
                    this.stop()
                } else {
                    this.gui.setScreen(null)
                }
            },
            Component.translatable("modsync.update.end.restart"),
            Component.translatable("modsync.update.end.restart.more"),
            Component.translatable("modsync.update.end.restart.yes"),
            Component.translatable("modsync.update.end.restart.no")
        ))
    }
}
