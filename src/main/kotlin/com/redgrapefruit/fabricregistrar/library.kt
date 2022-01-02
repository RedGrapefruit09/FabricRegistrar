package com.redgrapefruit.fabricregistrar

import net.minecraft.util.Identifier
import net.minecraft.util.registry.MutableRegistry
import net.minecraft.util.registry.Registry
import org.apache.logging.log4j.LogManager
import kotlin.reflect.KClass
import kotlin.reflect.KVisibility
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation

private val LOGGER = LogManager.getLogger("FabricRegistrar")

interface RegistryProvider {
    fun register(id: Identifier, content: Any)

    companion object {
        fun <T> forMap(map: MutableMap<Identifier, T>): RegistryProvider {
            return MapRegistryProvider(map)
        }

        fun <T> forRegistry(map: Registry<T>): RegistryProvider {
            if (map !is MutableRegistry) {
                throw RuntimeException("Tried to obtain registry provider for an immutable registry!")
            }

            return StandardRegistryProvider(map)
        }

        fun create(lambda: (id: Identifier, content: Any) -> Unit): RegistryProvider {
            return LambdaRegistryProvider(lambda)
        }
    }
}

private class LambdaRegistryProvider(private val impl: (id: Identifier, content: Any) -> Unit)
    : RegistryProvider {

    override fun register(id: Identifier, content: Any) {
        impl(id, content)
    }
}

class StandardRegistryProvider<T>(private val registry: MutableRegistry<T>) : RegistryProvider {
    override fun register(id: Identifier, content: Any) {
        Registry.register(registry, id, content as T)
    }
}

class MapRegistryProvider<T>(private val map: MutableMap<Identifier, T> = mutableMapOf()) : RegistryProvider {
    override fun register(id: Identifier, content: Any) {
        map[id] = content as T
    }
}

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class Registrar(val content: KClass<*>)

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
annotation class RegistryObject(val name: String = "{DETECT}")

data class DetectionMode(
    val publicOnly: Boolean = false,
    val annotatedOnly: Boolean = false,
    val namedOnly: Boolean = false
) {
    companion object {
        val default = DetectionMode(publicOnly = true)
    }
}

@DslMarker
annotation class RegistrySpecificationDSL

@RegistrySpecificationDSL
inline fun registries(configure: RegistrySpecificationScope.() -> Unit) {
    RegistrySpecificationScope().configure()
}

class RegistrySpecificationScope {
    @PublishedApi internal var detectionMode: DetectionMode = DetectionMode.default
    @PublishedApi internal var mod: String = "{UNSPECIFIED}"

    inline fun <reified T> registry(provider: RegistryProvider) {
        runRegistry(T::class, detectionMode, mod, provider)
    }

    inline fun <reified T> registry(registry: Registry<*>) {
        runRegistry(T::class, detectionMode, mod, RegistryProvider.forRegistry(registry))
    }

    inline fun <reified T> registry(map: MutableMap<Identifier, T>) {
        runRegistry(T::class, detectionMode, mod, RegistryProvider.forMap(map))
    }
}

fun runRegistry(clazz: KClass<*>, mode: DetectionMode, mod: String, provider: RegistryProvider) {
    // Initial checks
    if (mod == "{UNSPECIFIED}") {
        throw RegistryExecutionException("Mod ID hasn't been specified!")
    }
    if (!clazz.hasAnnotation<Registrar>()) {
        throw RegistryExecutionException("${clazz.simpleName!!} isn't annotated with @Registrar!")
    }
    if (clazz.objectInstance == null) {
        throw RegistryExecutionException("${clazz.simpleName!!} isn't an object!")
    }

    val registrar = clazz.findAnnotation<Registrar>()!!

    val cnt = clazz.declaredMemberProperties.size

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
                throw RegistryExecutionException("${obj.name} is an invalid registration name, since it contains uppercase characters!")
            }

            finalName = obj.name
        }

        // Registration
        val value = property.getter.call(clazz.objectInstance) ?: throw RegistryExecutionException("Registered property's value is null!")
        provider.register(Identifier(mod, finalName), value)
    }
}

class RegistryExecutionException(msg: String = "") : RuntimeException(msg)

fun String.hasUppercase(): Boolean {
    forEach { char ->
        if (char.isUpperCase()) return true
    }

    return false
}
