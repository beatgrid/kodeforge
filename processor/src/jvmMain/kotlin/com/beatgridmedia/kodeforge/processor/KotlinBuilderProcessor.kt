package com.beatgridmedia.kodeforge.processor

import com.beatgridmedia.kodeforge.annotation.Builder
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.KSVisitorVoid
import com.google.devtools.ksp.symbol.Nullability.NULLABLE
import com.google.devtools.ksp.validate
import java.io.OutputStream

private fun OutputStream.appendText(str: String) {
    this.write(str.toByteArray())
}
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
            val className = "${parent.simpleName.asString()}Builder"
            val qualifiedClassName = parent.qualifiedName ?: error("Could not get qualified class name from: $parent")
            val functionContainingFile = function.containingFile ?: error("Could not get containing file from $function")
            val file = codeGenerator.createNewFile(Dependencies(true, functionContainingFile), packageName , className)
            file.appendText("package $packageName\n\n")
            file.appendText("class $className{\n")
            function.parameters.forEach { parameter ->
                val parameterName = parameter.name?.asString() ?: error("Could not get parameter name for parameter: $parameter")
                val typeName = parameter.typeName
                file.appendText("    private var $parameterName: $typeName? = null\n")
                file.appendText("    fun $parameterName($parameterName: $typeName): $className = apply {\n")
                file.appendText("        this.$parameterName = $parameterName\n")
                file.appendText("    }\n\n")
            }
            file.appendText("    fun build(): ${qualifiedClassName.asString()} {\n")
            file.appendText("        return ${qualifiedClassName.asString()}(\n")
            for (parameter in function.parameters) {
                val isNullable = parameter.type.resolve().nullability == NULLABLE
                val parameterName = parameter.name?.asString() ?: error("Could not get parameter name for parameter: $parameter")
                file.appendText("            $parameterName = this.$parameterName" + (if (!isNullable) " ?: error(\"Required parameter $parameterName is not set\"),\n" else ",\n"))
            }
            file.appendText("        )\n")
            file.appendText("    }\n")
            file.appendText("}\n")
            file.close()
        }
    }
}

private val KSValueParameter.typeName: String
    get() {
        val baseName = this.type.resolve().declaration.qualifiedName?.asString() ?: error("Could not find qualified name for parameter: $this")
        val typeName = StringBuilder(baseName)
        val typeArgs = this.type.element?.typeArguments ?: emptyList()
        if (typeArgs.isNotEmpty()) {
            typeName.append("<")
            typeName.append(
                typeArgs.joinToString(", ") { typeArgument ->
                    val type = typeArgument.type?.resolve()
                    "${typeArgument.variance.label} ${type?.declaration?.qualifiedName?.asString() ?: error("Could not get qualified name for generic type parameter: $type")}" +
                            (if (type.nullability == NULLABLE) "?" else "")
                }
            )
            typeName.append(">")
        }
        return typeName.toString()
    }

class KotlinBuilderProcessorProvider : SymbolProcessorProvider {
    override fun create(
        environment: SymbolProcessorEnvironment
    ): SymbolProcessor {
        return KotlinBuilderProcessor(environment.codeGenerator, environment.logger)
    }
}