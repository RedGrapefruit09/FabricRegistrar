package com.redgrapefruit.fabricregistrar

import net.minecraft.util.Identifier
import net.minecraft.util.registry.MutableRegistry
import net.minecraft.util.registry.Registry
import kotlin.reflect.KClass

// Credits to https://github.com/DimensionalDevelopment/Matrix for being the inspiration for this project

/**
 * A [RegistryProvider] is a wrapper around a method of registering content that uses [Identifier]s as keys.
 *
 * The main reason for doing this is that not all content is stored within a
 * standard [Registry], for example, `FabricModelPredicateProviderRegistry` and even some examples in vanilla
 * Minecraft code!
 *
 * [RegistryProvider] **doesn't use generics**, as that would make trouble with the _lovely_ `Any?` types in
 * Kotlin reflection, so you have to cast your types in an unsafe manner.
 *
 * **There are built-in [RegistryProvider]s** for standard [Registry]s and simple [MutableMap]s. Create them
 * with [forRegistry] and [forMap].
 *
 * You don't have to implement this interface to define a [RegistryProvider], if you're a fan of excessive
 * lambda usage (like me), you can use the [create] function.
 */
interface RegistryProvider {
    /**
     * Register your [content] under a given [id]
     */
    fun register(id: Identifier, content: Any)

    companion object {
        /**
         * Creates a [RegistryProvider] backed by a simple Kotlin [MutableMap]. Uses [MapRegistryProvider].
         */
        fun <T> forMap(map: MutableMap<Identifier, T>): RegistryProvider {
            return MapRegistryProvider(map)
        }

        /**
         * Creates a [RegistryProvider] backed by a standard Minecraft [Registry]. Uses [StandardRegistryProvider].
         */
        fun <T> forRegistry(map: Registry<T>): RegistryProvider {
            if (map !is MutableRegistry) {
                throw RuntimeException("Tried to obtain registry provider for an immutable registry!")
            }

            return StandardRegistryProvider(map)
        }

        /**
         * Creates a custom [RegistryProvider] backed by a given lambda expression.
         */
        fun create(lambda: (id: Identifier, content: Any) -> Unit): RegistryProvider {
            return LambdaRegistryProvider(lambda)
        }
    }
}

/**
 * Define a given class as a [Registrar], meaning it can be scanned to find
 * [RegistryObject]s (implicitly or explicitly defined) and register them into the game.
 *
 * **Only works on Kotlin `object`s!**
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
annotation class Registrar(
    /**
     * What type of content to scan this [Registrar] for. One [Registrar] can only be used
     * for a single type of content.
     *
     * For example, your `ItemRegistry` will be annotated with `@Registrar(Item::class)`,
     * since it is used to register items.
     */
    val content: KClass<*>
)

/**
 * Defines an object within a [Registrar] that should be registered into the game.
 *
 * [RegistryObject] detection rules vary depending on your [DetectionMode]:
 *
 * - If [DetectionMode.publicOnly] is enabled, only public fields/properties will be scanned for [RegistryObject]s.
 * - If [DetectionMode.annotatedOnly] is enabled, **implicit [RegistryObject] search will be disabled**. Implicit
 * search means that any matching field/property that is of type specified in the [Registrar.content] field
 * will be interpreted as a [RegistryObject] without needing to attach this annotation.
 * - If [DetectionMode.namedOnly] is enabled, you won't be able to omit the name of the registered object
 * in favor of it being automatically generated from the field's/property's declared name.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
annotation class RegistryObject(
    /**
     * The name, under which this [RegistryObject] will be registered into the game.
     */
    val name: String = "{DETECT}"
)

/**
 * A selection of rules for detecting [RegistryObject]s within a [Registrar].
 *
 * See [RegistryObject]'s documentation for more.
 */
data class DetectionMode(
    val publicOnly: Boolean = false,
    val annotatedOnly: Boolean = false,
    val namedOnly: Boolean = false
) {
    companion object {
        /**
         * The default [DetectionMode] that will be used if you don't specify one
         * in [RegistrarSpecificationScope.detectionMode].
         */
        val default = DetectionMode(publicOnly = true)
    }
}

/**
 * Marks the DSL for specifying and running FabricRegistrar [Registrar]s.
 */
@DslMarker
annotation class RegistrarSpecificationDSL

/**
 * Opens the DSL block for defining and running your [Registrar]s.
 */
@RegistrarSpecificationDSL
inline fun registrars(configure: RegistrarSpecificationScope.() -> Unit) {
    RegistrarSpecificationScope().configure()
}

class RegistrarSpecificationScope {
    /**
     * Specify the [DetectionMode] for your [Registrar] definitions.
     */
    var detectionMode: DetectionMode = DetectionMode.default

    /**
     * Specify your [mod] ID, a namespace for your [Identifier]s.
     */
    var mod: String = "{UNSPECIFIED}"

    /**
     * Run a [Registrar] class with a custom [RegistryProvider].
     */
    inline fun <reified T> registrar(provider: RegistryProvider) {
        runRegistry(T::class, detectionMode, mod, provider)
    }

    /**
     * Run a [Registrar] class with a [Registry]-based [RegistryProvider]. See [RegistryProvider.forRegistry].
     */
    inline fun <reified T> registrar(registry: Registry<*>) {
        runRegistry(T::class, detectionMode, mod, RegistryProvider.forRegistry(registry))
    }

    /**
     * Run a [Registrar] class with a [MutableMap]-based [RegistryProvider]. See [RegistryProvider.forMap].
     */
    inline fun <reified T> registrar(map: MutableMap<Identifier, T>) {
        runRegistry(T::class, detectionMode, mod, RegistryProvider.forMap(map))
    }
}

/**
 * A type of [RuntimeException] (unchecked) that occurred when trying to execute a [Registrar].
 *
 * Types of [RegistrarExecutionException]s and solutions to them:
 *
 * - Mod ID hasn't been specified! =>
 * set [RegistrarSpecificationScope.mod]'s value before calling [RegistrarSpecificationScope.registrar]
 * - {CLASS_NAME} isn't annotated with @Registrar! =>
 * annotate your registrar object with the [Registrar] annotation.
 * - {CLASS_NAME} isn't an object! =>
 * only Kotlin objects are supported as [Registrar] targets, make your registrar an object.
 * - {REGISTRY_OBJECT_NAME} is an invalid registration name, since it contains uppercase characters! =>
 * you're manually specifying a name in a [RegistryObject] declaration, which contains uppercase characters in it, replace them with lowercase ones.
 * - {PROPERTY_NAME}'s value is null! =>
 * you forgot to put a value into one of your registered objects, or you need to make your [DetectionMode] stricter if you don't want that property being registered.
 */
class RegistrarExecutionException(msg: String = "") : RuntimeException(msg)
