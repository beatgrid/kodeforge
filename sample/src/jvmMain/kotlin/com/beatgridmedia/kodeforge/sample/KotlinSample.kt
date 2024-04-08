package com.beatgridmedia.kodeforge.sample

class KotlinSample {
    fun createFoo(): Foo {
        return FooBuilder().field1(1).field2("foo").field3("bar").field4("baz").build()
    }

    fun copyFoo(template: Foo, field1: Int): Foo {
        return FooBuilder(template).field1(field1).build()
    }
}