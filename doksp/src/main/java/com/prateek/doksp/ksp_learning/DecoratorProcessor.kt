package com.prateek.doksp.ksp_learning

import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.validate
import com.prateek.doksp.ksp_learning.annotations.Decorator

class DecoratorProcessor(
    private val codeGenerator: CodeGenerator,
    private val logger: KSPLogger
) : SymbolProcessor {

    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(
            requireNotNull(
                Decorator::class.qualifiedName,
                lazyMessage = { "Decorator is not annotated on any class" })
        )

        val visitor = DecoratorVisitor(codeGenerator, logger)
        symbols.filter { it is KSClassDeclaration && it.validate() }.forEach {
            it.accept(visitor, Unit)
        }

        return symbols.filter { !it.validate() }.toList()
    }

    override fun onError() {
        super.onError()

    }

}