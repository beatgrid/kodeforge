package com.beatgridmedia.kodeforge.sample

import com.beatgridmedia.kodeforge.annotation.Builder

@Builder
class Foo(val field1: Int, val field2: String, val field3: String?, val field4: String? = null, field5: String, private val field6: String) {
    public var field7: String? = null

    private var field8: String? = null
    public var field9: String? = null
        private set
}

sealed class Bar {
    @Builder
    class Baz(val value: String) : Bar()
}
