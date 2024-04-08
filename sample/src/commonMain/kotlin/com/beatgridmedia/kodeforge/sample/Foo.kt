package com.beatgridmedia.kodeforge.sample

import com.beatgridmedia.kodeforge.annotation.Builder

@Builder
class Foo(val field1: Int, val field2: String, val field3: String?, val field4: String? = null) {
}