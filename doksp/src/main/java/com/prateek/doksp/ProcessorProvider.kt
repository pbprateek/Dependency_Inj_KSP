package com.prateek.doksp

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider

class ProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        //return DecoratorProcessor(environment.codeGenerator, environment.logger)
        return DiProcessor(environment.codeGenerator, environment.logger)
    }
}