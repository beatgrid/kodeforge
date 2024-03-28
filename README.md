# KodeForge

A multiplatform code generation library based on KSP.

## Features

- @Builder annotation: automatically generate a builder (with name "${classname}Builder") for use in Java code from a kotlin class.

## Usage

- Add the following to your `build.gradle` or `build.gradle.kts`

```groovy
plugins {
    id("com.google.devtools.ksp")
}

kotlin {
    sourceSets {
        commonMain {
            dependendencies {
                implementation("com.beatgridmedia.kodeforge:annotation:$kodeforge_version")
            }
        }
    }
}

dependencies {
    add("kspJvm", "com.beatgridmedia.kodeforge:processor:$kodeforge_version")
}
```

- To any kotlin class, add the `@Builder` annotation:

```kotlin
import com.beatgridmedia.kodeforge.annotation.Builder

@Builder // Will generate class `FooBuilder` in the same package
data class Foo(val foo: String, val bar: String)
```

The generated builder class (with all parameters which are present in the primary constructor) supports both default and nullable parameters. 
Any parameter which does not have a default should be set explicitly otherwise an exception is thrown.

Example usage of builder class:

```kotlin
fun buildFoo(): Foo {
    return FooBuiler()
        .foo("foo")
        .bar("bar")
        .build()
}
```