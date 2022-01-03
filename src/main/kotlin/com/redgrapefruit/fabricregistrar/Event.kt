package com.redgrapefruit.fabricregistrar

import net.fabricmc.fabric.api.event.EventFactory
import net.minecraft.util.Identifier
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

fun interface ObjectRegisterCallback {
    fun onObjectRegistered(
        clazz: KClass<*>,
        mode: DetectionMode,
        mod: String,
        provider: RegistryProvider,
        property: KProperty1<*, *>)

    companion object {
        val EVENT = EventFactory.createArrayBacked(ObjectRegisterCallback::class.java)
        { listeners ->
            ObjectRegisterCallback { clazz, mode, mod, provider, property ->
                listeners.forEach { it.onObjectRegistered(clazz, mode, mod, provider, property) }
            }
        }
    }
}

fun interface RegistryProviderUseCallback {
    fun onProviderUsed(provider: RegistryProvider, id: Identifier, obj: Any)

    companion object {
        val EVENT = EventFactory.createArrayBacked(RegistryProviderUseCallback::class.java)
        { listeners ->
            RegistryProviderUseCallback { provider, id, obj ->
                listeners.forEach { it.onProviderUsed(provider, id, obj) }
            }
        }
    }
}

fun interface RegistrarRegisterCallback {
    fun onRegistrarRegistered(
        clazz: KClass<*>,
        mode: DetectionMode,
        mod: String,
        provider: RegistryProvider)

    companion object {
        val EVENT = EventFactory.createArrayBacked(RegistrarRegisterCallback::class.java)
        { listeners ->
            RegistrarRegisterCallback { clazz, mode, mod, provider ->
                listeners.forEach { it.onRegistrarRegistered(clazz, mode, mod, provider) }
            }
        }
    }
}
