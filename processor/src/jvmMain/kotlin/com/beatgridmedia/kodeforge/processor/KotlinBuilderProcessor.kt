package com.beatgridmedia.kodeforge.processor

import com.beatgridmedia.kodeforge.annotation.Builder
import com.google.devtools.ksp.isPrivate
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.KSVisitorVoid
import com.google.devtools.ksp.symbol.Nullability
import com.google.devtools.ksp.symbol.Nullability.NULLABLE
import com.google.devtools.ksp.validate
import java.io.OutputStream

private fun OutputStream.appendLine(str: String = "") {
    this.write(str.toByteArray())
    this.write('\n'.code)
}

private val Nullability.symbol: String
    get() = if (this == NULLABLE) "?" else ""

class KotlinBuilderProcessor(
    val codeGenerator: CodeGenerator,
    val logger: KSPLogger
) : SymbolProcessor {
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(Builder::class.qualifiedName!!)
        val ret = symbols.filter { !it.validate() }.toList()
        symbols
            .filter { it is KSClassDeclaration && it.validate() }
            .forEach { it.accept(BuilderVisitor(), Unit) }
        return ret
    }

    inner class BuilderVisitor : KSVisitorVoid() {
        override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
            val primaryConstructor = classDeclaration.primaryConstructor ?: error("Could not get primary constructor for class: ${classDeclaration.qualifiedName}")
            primaryConstructor.accept(this, data)
        }

        override fun visitFunctionDeclaration(function: KSFunctionDeclaration, data: Unit) {
            val parent = function.parentDeclaration as? KSClassDeclaration ?: error("Expected parent declaration to be a class declaration but was: ${function.parentDeclaration}")
            val packageName = parent.containingFile?.packageName?.asString() ?: error("Could not get containing file from: $parent")
            val className = parent.builderClassName
            val qualifiedClassName = parent.qualifiedName ?: error("Could not get qualified class name from: $parent")
            val functionContainingFile = function.containingFile ?: error("Could not get containing file from $function")
            val parameters = function.parameters
            val properties = parent.getAllProperties().toList()
            val file = codeGenerator.createNewFile(Dependencies(true, functionContainingFile), packageName , className)
            file.appendLine("package $packageName")
            file.appendLine()
            file.appendLine("import kotlin.reflect.KParameter")
            file.appendLine("import kotlin.reflect.full.primaryConstructor")
            file.appendLine("import kotlin.reflect.full.memberProperties")
            file.appendLine("import kotlin.reflect.jvm.isAccessible")
            file.appendLine()
            file.appendLine("class $className @JvmOverloads constructor(private val allowNullAsImplicitDefault: Boolean = false) {")
            file.appendLine("    @JvmOverloads constructor(other: ${qualifiedClassName.asString()}, allowNullAsImplicitDefault: Boolean = false): this(allowNullAsImplicitDefault) {")
            parameters.forEach { parameter ->
                parameter.type
                val typeName = parameter.typeName
                val parameterName = parameter.name?.asString() ?: error("Could not get parameter name for parameter: $parameter")
                val property: KSPropertyDeclaration? = properties.firstOrNull { it.simpleName.asString() == parameterName }
                if (property != null) {
                    if (property.isPrivate()) {
                        file.appendLine("        ${qualifiedClassName.asString()}::class.memberProperties.find { it.name == \"$parameterName\" }?.also {")
                        file.appendLine("            it.isAccessible = true")
                        file.appendLine("            this.$parameterName(it.get(other) as $typeName)")
                        file.appendLine("        }")
                    } else {
                        file.appendLine("        this.$parameterName(other.$parameterName)")
                    }
                }
            }
            file.appendLine("    }")
            file.appendLine()
            parameters.forEach { parameter ->
                val parameterName = parameter.name?.asString() ?: error("Could not get parameter name for parameter: $parameter")
                val typeName = parameter.typeName
                val isNullable = parameter.type.resolve().nullability == NULLABLE
                file.appendLine("    private var _$parameterName: $typeName? = null")
                file.appendLine("    private var _${parameterName}Set: Boolean = false")
                file.appendLine("    fun $parameterName($parameterName: $typeName${if (isNullable) "?" else ""}): $className = apply {")
                file.appendLine("        this._${parameterName}Set = true")
                file.appendLine("        this._$parameterName = $parameterName")
                file.appendLine("    }")
                file.appendLine()
            }
            file.appendLine("    fun build(): ${qualifiedClassName.asString()} {")
            file.appendLine("        val primaryConstructor = ${qualifiedClassName.asString()}::class.primaryConstructor ?: error(\"There is no primary constructor present in class ${qualifiedClassName.asString()}\")")
            file.appendLine("        val arguments = mutableMapOf<KParameter, Any?>()")
            file.appendLine("        val constructorParameters = primaryConstructor.parameters.associateBy { it.name ?: error(\"Could not get name for parameter in primary constructor\") }")
            parameters.forEach { parameter ->
                val isNullable = parameter.type.resolve().nullability == NULLABLE
                val hasDefault = parameter.hasDefault
                val parameterName = parameter.name?.asString() ?: error("Could not get parameter name for parameter: $parameter")
                if (!hasDefault) {
                    if (isNullable) {
                        file.appendLine("        if (!allowNullAsImplicitDefault) require(_${parameterName}Set) { \"Required property '$parameterName' is not set\" }")
                    } else {
                        file.appendLine("        require(_${parameterName}Set) { \"Required property '$parameterName' is not set\" }")
                    }
                    file.appendLine("        arguments[constructorParameters[\"$parameterName\"]!!] = _$parameterName" + (if (isNullable) "" else "!!"))
                } else {
                    file.appendLine("        if (_${parameterName}Set) arguments[constructorParameters[\"$parameterName\"]!!] = _$parameterName" + (if (isNullable) "" else "!!"))
                }
            }
            file.appendLine("        return primaryConstructor.callBy(args = arguments)")
            file.appendLine("    }")
            file.appendLine("}")
            file.close()
        }
    }
}

private val KSTypeReference.typeName: String
    get() {
        val baseName = this.resolve().declaration.qualifiedName?.asString() ?: error("Could not find qualified name for parameter: $this")
        val typeName = StringBuilder(baseName)
        val typeArgs = this.element?.typeArguments ?: emptyList()
        if (typeArgs.isNotEmpty()) {
            typeName.append("<")
            typeName.append(
                typeArgs.joinToString(", ") { typeArgument ->
                    val subTypeName = typeArgument.type?.typeName ?: ""
                    val nullabilitySymbol = typeArgument.type?.resolve()?.nullability?.symbol ?: ""
                    val varianceModifier = typeArgument.variance.label
                    if (varianceModifier.isEmpty()) {
                        "$subTypeName$nullabilitySymbol".trim()
                    } else {
                        "$varianceModifier $subTypeName$nullabilitySymbol".trim()
                    }
                }
            )
            typeName.append(">")
        }
        return typeName.toString()
    }

private val KSClassDeclaration.builderClassName: String
    get() {
        if (this.classKind != ClassKind.CLASS) {
            error("Builder annotation is only supported for class types")
        }
        val buffer = StringBuilder()
        val classDeclarations = ArrayDeque<KSClassDeclaration>()
        var parent: KSClassDeclaration? = this
        while (parent != null) {
            classDeclarations.addFirst(parent)
            parent = parent.parentDeclaration as? KSClassDeclaration
        }
        classDeclarations.forEach {
            buffer.append(it.simpleName.asString())
        }
        buffer.append("Builder")
        return buffer.toString()
    }

private val KSValueParameter.typeName: String
    get() {
        return this.type.typeName
    }

class KotlinBuilderProcessorProvider : SymbolProcessorProvider {
    override fun create(
        environment: SymbolProcessorEnvironment
    ): SymbolProcessor {
        return KotlinBuilderProcessor(environment.codeGenerator, environment.logger)
    }
}
