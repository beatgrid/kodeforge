package com.beatgridmedia.kodeforge.sample;

public class JavaSample {
    public Foo createFoo() {
        return new FooBuilder().field1(1).field2("foo").field3("bar").field4("baz").build();
    }

    public Foo copyFoo(Foo template, String field1) {
        return new FooBuilder(template).field1(1).build();
    }
}
