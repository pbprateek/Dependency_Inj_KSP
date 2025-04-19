package com.prateek.doksp.ksp_learning

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.KSVisitorVoid
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterSpec
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toTypeName
import com.squareup.kotlinpoet.ksp.writeTo


data class Func(val functionName: String, val returnType: KSTypeReference)

class DecoratorVisitor(private val codeGenerator: CodeGenerator, private val logger: KSPLogger) :
    KSVisitorVoid() {

    private val functions = mutableListOf<Func>()

    override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
        super.visitClassDeclaration(classDeclaration, data)

        logger.warn("Class name: $classDeclaration")
        classDeclaration.getDeclaredFunctions()
            .forEach { func ->
                logger.warn("Func Name: $func")
                func.accept(this@DecoratorVisitor, Unit)
            }
        val packageName = classDeclaration.packageName.asString()
        val classname = "Ksp_$classDeclaration"

        //CodeGen via kotlinPoet
        val fileSpec = FileSpec.builder(packageName, classname).apply {
            addType(
                TypeSpec.classBuilder(classname)
                    .primaryConstructor(
                        FunSpec.constructorBuilder()
                            .addParameter(getConstructionParam(classDeclaration))
                            .build()
                    )
                    .addProperty(
                        PropertySpec.builder(
                            "$classDeclaration".lowercase(),
                            getTypeName(classDeclaration)
                        )
                            .initializer("$classDeclaration".lowercase())
                            .build()
                    )
                    .addSuperinterface(getTypeName(classDeclaration))
                    .addModifiers(KModifier.OPEN, KModifier.PUBLIC)
                    .addFunctions(getAllFunctions(classDeclaration))
                    .build()
            )
        }
            .build()

        fileSpec.writeTo(codeGenerator, true)
    }

    private fun getAllFunctions(classDeclaration: KSClassDeclaration): Iterable<FunSpec> {
        return functions.map {
            val returnType = it.returnType.toTypeName()
            val paramName = classDeclaration.toString().lowercase()

            FunSpec.builder(it.functionName)
                .addModifiers(KModifier.OVERRIDE)
                .returns(returnType)
                .addStatement("return $paramName.${it.functionName}()")
                .build()
        }
    }

    private fun getConstructionParam(classDeclaration: KSClassDeclaration): ParameterSpec {
        return ParameterSpec.builder("$classDeclaration".lowercase(), getTypeName(classDeclaration))
            .build()
    }

    private fun getTypeName(classDeclaration: KSClassDeclaration): TypeName {
        return ClassName(classDeclaration.packageName.asString(), "$classDeclaration")
    }

    override fun visitFunctionDeclaration(function: KSFunctionDeclaration, data: Unit) {
        super.visitFunctionDeclaration(function, data)

        function.returnType?.accept(this, Unit)

        //Add to list
        function.returnType?.let {
            functions.add(Func("$function", it))
        }

    }

    override fun visitTypeReference(typeReference: KSTypeReference, data: Unit) {
        super.visitTypeReference(typeReference, data)

        logger.warn("Return Type: $typeReference")


    }


}