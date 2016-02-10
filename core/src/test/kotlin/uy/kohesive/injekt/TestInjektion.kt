package uy.kohesive.injekt.tests

import org.junit.Test
import uy.kohesive.injekt.*
import uy.kohesive.injekt.api.*
import uy.kohesive.injekt.registry.default.DefaultRegistrar
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import kotlin.test.*

class MockLogger(name: String?, clazz: Class<*>?) {
    val result: String = name ?: clazz?.getName() ?: "!!error!!"
}

class TestInjektion {
    companion object : InjektMain() {
        // platformStatic public fun main(args: Array<String>) {
            // the Injekt module goes on something that you know will be instantiated first, say the companion object of where you put
            // your static main.  Or the first class it creates.
        // }

        override fun InjektRegistrar.registerInjectables() {
            // import other prepackaged injections
            importModule(OtherModuleWithPrepackagedInjektions)
            importModule(ExtraModuleWithInjektions)

            // factory for one instance per thread
            addPerThreadFactory { NotThreadSafeConnection(Thread.currentThread().toString()) }
            // instantiate now, not later
            addSingleton(NotLazy("Freddy"))

            // register the descendant class to be created, allow any ancestor classes to be used
            addSingleton(DescendantThing("family"))
            addAlias<DescendantThing, AncestorThing>()

            // logging is special!
            addLoggerFactory<MockLogger>({ name -> MockLogger(name,null) }, { klass -> MockLogger(null, klass) })
        }
    }

    // now we can inject using delegation in any class
    val swm: SomethingSingleton by injectValue()  // inject at instantiation
    val many: ManyMultiples by injectLazy() // inject when accessed
    val many2: ManyMultiples by injectLazy()
    val worker: NotLazy by injectLazy()

    val LOG: MockLogger by injectLogger()
    val LOG_BYNAME: MockLogger by injectLogger("testy")
    val LOG_BYCLASS: MockLogger by injectLogger(TestInjektion::class)

    @Test fun testInjectedMembers() {
        assertEquals("Hi, I'm single", swm.name)
        assertEquals(swm, Injekt.get<SomethingSingleton>()) // ask for a value directly
        assertEquals("Freddy", worker.name)
        assertEquals(worker, Injekt.get<NotLazy>()) // should always get same singletons
        assertTrue(worker === Injekt.get<NotLazy>()) // should always get same singletons

        assertNotEquals(many.whenCreated, many2.whenCreated)
        assertNotEquals(Injekt.get<ManyMultiples>(), Injekt.get<ManyMultiples>())
    }

    @Test fun testInjectionOfThreadSingletons() {
        val sync = CountDownLatch(3)
        val threadVals = ConcurrentLinkedQueue<NotThreadSafeConnection>()
        for (i in 0..2) {
            Thread() {
                val myThreadValue1 = Injekt.get<NotThreadSafeConnection>()
                val myThreadValue2 = Injekt.get<NotThreadSafeConnection>()
                assertTrue(myThreadValue1 === myThreadValue2)
                threadVals.add(myThreadValue1)
                sync.countDown()
            }.start()
        }
        sync.await()

        assertEquals(3, threadVals.size)
        val results = threadVals.toList()
        assertNotEquals(results[0], results[1])
        assertNotEquals(results[0], results[2])
        assertNotEquals(results[1], results[2])
    }

    @Test fun testInjectionInMethodParameters() {
        data class ConstructedWithInjektion(val mySingleItem: SomethingSingleton = Injekt.get())
        data class Constructed2WithInjektion(val mySingleItem: SomethingSingleton = Injekt())

        fun doSomething(myWorker: NotLazy = Injekt.get()) = myWorker.name

        assertEquals("Hi, I'm single", ConstructedWithInjektion().mySingleItem.name)
        assertEquals("Hi, I'm single", Constructed2WithInjektion().mySingleItem.name)
        assertEquals("Freddy", doSomething())
    }

    @Test fun testNestedInjection() {
        data class ConstructedInFactory(val mySingleItem: SomethingSingleton)

        Companion.scope.addSingletonFactory {  ConstructedInFactory(Injekt.get<SomethingSingleton>()) }

        assertEquals("Hi, I'm single", Injekt.get<ConstructedInFactory>().mySingleItem.name)
    }

    @Test fun testAnyDescendantLevel() {
        assertEquals("family", Injekt.get<DescendantThing>().name)
        assertEquals("family", Injekt.get<AncestorThing>().name)
    }

    @Test fun testLogging() {
        assertNotNull(LOG)
        assertEquals("uy.kohesive.injekt.tests.TestInjektion", LOG.result)
        assertNotNull(LOG_BYNAME)
        assertEquals("testy", LOG_BYNAME.result)
        assertNotNull(LOG_BYCLASS)
        assertEquals("uy.kohesive.injekt.tests.TestInjektion", LOG_BYCLASS.result)

        // sending in anything not a string or a class will use the Class of the item
        assertEquals("uy.kohesive.injekt.tests.NotLazy", Injekt.logger<MockLogger>(NotLazy("asd")).result)
    }

    @Test fun testKeyedInjection() {
        Companion.scope.addPerKeyFactory { key: String -> KeyedThing("$key - ${System.currentTimeMillis()}") }
        val one = Injekt.get<KeyedThing>("one")
        val two = Injekt.get<KeyedThing>("two")
        assertNotEquals(one,two)
        val oneAgain = Injekt.get<KeyedThing>("one")
        assertEquals(one, oneAgain)
    }

    @Test fun testGetWithBrackets() {
        Companion.scope.addPerKeyFactory { key: String -> KeyedThing("$key - ${System.currentTimeMillis()}") }
        val one = Injekt.get<KeyedThing>("one")
        @Suppress("OPERATOR_MODIFIER_REQUIRED")
        val twoAgain: KeyedThing = Injekt["two"]
        assertNotEquals(one,twoAgain)
    }


    @Test fun testDefaultedGet() {
        val one = Injekt.getOrElse<NotExisting>(NotExisting("one"))
        val two = Injekt.getOrElse<NotExisting>() { NotExisting("two") }
        assertEquals("one", one.name)
        assertEquals("two", two.name)
    }

    @Test fun testUnregistgeredTypeException() {
        try {
            @Suppress("UNUSED_VARIABLE")
            val three = Injekt.get<NotExisting>()
            fail("a get for non registered class should throw exception if not defaulted")
        } catch (ex: InjektionException) {
            // nop, expected
        }
    }

    @Test fun testNullGet() {
        val one = Injekt.getOrNull<NotExisting>() ?: NotExisting("one")
        assertEquals("one", one.name)
    }

    @Test fun testScopeDescendant() {
        class MyActivityModule: InjektScopedMain(InjektScope(DefaultRegistrar())) {
            override fun InjektRegistrar.registerInjectables() {
                // override with local value
                addSingletonFactory { NotLazy("Happy Dancer") }
                // import other registrations
                importModule(OtherModuleWithPrepackagedInjektions)
                // delegate to global scope:
                addSingletonFactory { Injekt.get<DescendantThing>() }
            }
        }

        val myActivityModule = MyActivityModule().scope

        assertEquals("Happy Dancer", myActivityModule.get<NotLazy>().name)
        assertEquals("family", myActivityModule.get<DescendantThing>().name)
        assertEquals("Hi, I'm single", myActivityModule.get<SomethingSingleton>().name)

        val x = object {
            val localScope = MyActivityModule().scope
            val notLazy: NotLazy by localScope.injectValue()
        }

        assertEquals("Happy Dancer", x.notLazy.name)

        class MyActivityScope : InjektScope(DefaultRegistrar()) {
            init {
                // override with local value
                addSingletonFactory { NotLazy("Happy Runner") }
                // import other registrations
                importModule(OtherModuleWithPrepackagedInjektions)
                // delegate to global scope:
                addSingletonFactory { Injekt.get<DescendantThing>() }
            }
        }

        val myActivityScope = MyActivityScope()

        assertEquals("Happy Runner", myActivityScope.get<NotLazy>().name)
        assertEquals("family", myActivityScope.get<DescendantThing>().name)
        assertEquals("Hi, I'm single", myActivityScope.get<SomethingSingleton>().name)
    }

    @Test fun testScopedFactories() {

        class MyThing(scope: InjektScope) {
            val notLazy: NotLazy by injectLazy()
            val globalThing: DescendantThing by injectValue()
        }

        open class LocalScoped(protected val scope: InjektScope) {
            public inline fun <reified T: Any> injectLazy(): Lazy<T> {
                return scope.injectLazy<T>()
            }

            public inline fun <reified T: Any> injectValue(): Lazy<T> {
                return scope.injectValue<T>()
            }

            // TODO: implement any others you want to override to NOT call the global, keyed, logger, ...
        }

        class MyController(scope: InjektScope): LocalScoped(scope) {
            val notLazy: NotLazy by injectLazy()
            val globalThing: DescendantThing by injectValue()
            val thing: MyThing by injectValue()
        }

        class MyActivityScope : InjektScope(DefaultRegistrar()) {
            init {
                // things that share my local scope, use our custom extension function
                addScopedSingletonFactory { MyController(this) }
                addScopedFactory { MyThing(this) }
                // local registrations
                addSingletonFactory { NotLazy("Happy Runner") }
                // import other registrations
                importModule(OtherModuleWithPrepackagedInjektions)
                // delegate to global scope:
                addSingletonFactory { Injekt.get<DescendantThing>() }
            }
        }

        class MyActivity(): LocalScoped(MyActivityScope()) {
            val globalThing: DescendantThing by injectLazy()
            val notLazy: NotLazy by injectValue()
            val controller: MyController by injectValue()
            val thing: MyThing by injectValue()
        }

        val activity = MyActivity()
        assertEquals("Happy Runner", activity.notLazy.name)
        assertEquals("family", activity.globalThing.name)
        assertEquals("Happy Runner", activity.controller.notLazy.name)
        assertEquals("family", activity.controller.globalThing.name)

        assertTrue(activity.notLazy === activity.controller.notLazy, "Instances should be the same for singletons")
        assertTrue(activity.globalThing === activity.controller.globalThing,  "Instances should be the same for singletons")

        assertTrue(activity.thing !== activity.controller.thing, "Multi value factory instances should all be unique")

    }

}

data class NotThreadSafeConnection(val whatThreadMadeMe: String)
data class NotLazy(val name: String)
data class KeyedThing(val name: String)

open class AncestorThing(val name: String)
class DescendantThing(name: String): AncestorThing(name)

data class NotExisting(val name: String)


// === code can make common things ready for injection in the best way possible, if these were other modules or packages
//     they have defined some importable injections:

object OtherModuleWithPrepackagedInjektions: InjektModule {
    override fun InjektRegistrar.registerInjectables() {
        // lazy factory for singleton
        addSingletonFactory { SomethingSingleton("Hi, I'm single") }
    }
}

data class SomethingSingleton(val name: String)

// === and more...

object ExtraModuleWithInjektions : InjektModule {
    override fun InjektRegistrar.registerInjectables() {
        // factory for new instance per use
        addFactory { ManyMultiples() }
    }
}

data class ManyMultiples(val whenCreated: Long = System.currentTimeMillis()) {
    init {
        Thread.sleep(1) // let's not create two with the same milliseconds for this test
    }
}

