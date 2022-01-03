package com.redgrapefruit.fabricregistrar

import net.minecraft.util.Identifier
import net.minecraft.util.registry.MutableRegistry
import net.minecraft.util.registry.Registry
import kotlin.reflect.KClass

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
        fun <T> forRegistry(registry: Registry<T>): RegistryProvider {
            if (registry !is MutableRegistry) {
                throw RuntimeException("Tried to obtain registry provider for an immutable registry!")
            }

            return StandardRegistryProvider(registry)
        }

        /**
         * Creates a custom [RegistryProvider] backed by a given lambda expression.
         */
        fun create(lambda: (id: Identifier, content: Any) -> Unit): RegistryProvider {
            return LambdaRegistryProvider(lambda)
        }

        internal fun internalRegister(id: Identifier, content: Any, provider: RegistryProvider) {
            provider.register(id, content)
            RegistryProviderUseCallback.EVENT.invoker().onProviderUsed(provider, id, content)
        }
    }
}

/**
 * A [RegistryProvider] implementation backed by a lambda expression.
 */
class LambdaRegistryProvider(private val impl: (id: Identifier, content: Any) -> Unit)
    : RegistryProvider {

    override fun register(id: Identifier, content: Any) {
        impl(id, content)
    }
}

/**
 * A [RegistryProvider] implementation backed by a standard Minecraft [Registry].
 */
class StandardRegistryProvider<T>(private val registry: MutableRegistry<T>) : RegistryProvider {
    override fun register(id: Identifier, content: Any) {
        Registry.register(registry, id, content as T)
    }
}

/**
 * A [RegistryProvider] implementation backed by a simple [MutableMap].
 */
class MapRegistryProvider<T>(private val map: MutableMap<Identifier, T>) : RegistryProvider {
    override fun register(id: Identifier, content: Any) {
        map[id] = content as T
    }
}
