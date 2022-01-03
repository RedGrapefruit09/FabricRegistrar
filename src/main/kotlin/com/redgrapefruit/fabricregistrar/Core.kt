package com.redgrapefruit.fabricregistrar

import net.minecraft.util.Identifier
import net.minecraft.util.registry.Registry
import kotlin.reflect.KClass

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
