package com.redgrapefruit.fabricregistrar

import net.minecraft.util.Identifier
import kotlin.reflect.KClass
import kotlin.reflect.KVisibility
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation

@PublishedApi internal fun runRegistry(clazz: KClass<*>, mode: DetectionMode, mod: String, provider: RegistryProvider) {
    // Initial checks
    if (mod == "{UNSPECIFIED}") {
        throw RegistrarExecutionException("Mod ID hasn't been specified!")
    }
    if (!clazz.hasAnnotation<Registrar>()) {
        throw RegistrarExecutionException("${clazz.simpleName!!} isn't annotated with @Registrar!")
    }
    if (clazz.objectInstance == null) {
        throw RegistrarExecutionException("${clazz.simpleName!!} isn't an object!")
    }

    val registrar = clazz.findAnnotation<Registrar>()!!

    for (property in clazz.declaredMemberProperties) {
        // Checks
        if (mode.publicOnly && property.visibility!! != KVisibility.PUBLIC) continue
        if (mode.annotatedOnly && !property.hasAnnotation<RegistryObject>()) continue
        if (mode.namedOnly) {
            if (!property.hasAnnotation<RegistryObject>()) {
                continue
            } else {
                if (property.findAnnotation<RegistryObject>()!!.name == "{DETECT}") continue
            }
        }
        if (!registrar.content.isInstance(property.getter.call(clazz.objectInstance))) continue

        // Naming
        var finalName = property.name.lowercase()

        property.findAnnotation<RegistryObject>()?.let { obj ->
            if (obj.name.hasUppercase()) {
                throw RegistrarExecutionException("${obj.name} is an invalid registration name, since it contains uppercase characters!")
            }

            finalName = obj.name
        }

        // Registration
        val value = property.getter.call(clazz.objectInstance) ?: throw RegistrarExecutionException("${property.name}'s value is null!")
        RegistryProvider.internalRegister(Identifier(mod, finalName), value, provider)
        ObjectRegisterCallback.EVENT.invoker().onObjectRegistered(clazz, mode, mod, provider, property)
    }

    RegistrarRegisterCallback.EVENT.invoker().onRegistrarRegistered(clazz, mode, mod, provider)
}

fun String.hasUppercase(): Boolean {
    forEach { char ->
        if (char.isUpperCase()) return true
    }

    return false
}
