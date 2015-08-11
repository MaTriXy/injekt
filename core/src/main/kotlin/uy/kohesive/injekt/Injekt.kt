package uy.kohesive.injekt

import uy.kohesive.injekt.registry.default.DefaultRegistrar
import kotlin.properties.Delegates
import kotlin.properties.ReadOnlyProperty

public volatile var Injekt: InjektScope = InjektScope(DefaultRegistrar())

public abstract class InjektMain : InjektScopedMain(Injekt)

// top level Injekt scope

public inline fun <reified T> Delegates.injectLazy(): ReadOnlyProperty<Any?, T> {
    return kotlin.properties.Delegates.lazy { Injekt.getInstance(javaClass<T>()) }
}

public inline fun <reified T> Delegates.injectValue(): ReadOnlyProperty<Any?, T> {
    val value: T = Injekt.getInstance(javaClass<T>())
    return object : ReadOnlyProperty<Any?, T> {
        public override fun get(thisRef: Any?, desc: PropertyMetadata): T {
            return value
        }
    }
}

public inline fun <reified T> Delegates.injectLazy(key: Any): ReadOnlyProperty<Any?, T> {
    return kotlin.properties.Delegates.lazy {
        Injekt.getKeyedInstance(javaClass<T>(), key)
    }
}

public inline fun <reified T> Delegates.injectValue(key: Any): ReadOnlyProperty<Any?, T> {
    val value: T = Injekt.getKeyedInstance(javaClass<T>(), key)
    return object : ReadOnlyProperty<Any?, T> {
        public override fun get(thisRef: Any?, desc: PropertyMetadata): T {
            return value
        }
    }
}

public inline fun <reified R, reified T> Delegates.injectLogger(): ReadOnlyProperty<R, T> {
    val value: T = Injekt.getLogger(javaClass<T>(), javaClass<R>())
    return object : ReadOnlyProperty<R, T> {
        public override fun get(thisRef: R, desc: PropertyMetadata): T {
            return value
        }
    }
}

public inline fun <reified R, reified T> Delegates.injectLogger(byClass: Class<*>): ReadOnlyProperty<R, T> {
    val value: T = Injekt.getLogger(javaClass<T>(), byClass)
    return object : ReadOnlyProperty<R, T> {
        public override fun get(thisRef: R, desc: PropertyMetadata): T {
            return value
        }
    }
}

public inline fun <reified R, reified T> Delegates.injectLogger(byName: String): ReadOnlyProperty<R, T> {
    val value: T = Injekt.getLogger(javaClass<T>(), byName)
    return object : ReadOnlyProperty<R, T> {
        public override fun get(thisRef: R, desc: PropertyMetadata): T {
            return value
        }
    }
}

