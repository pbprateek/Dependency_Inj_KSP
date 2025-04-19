package com.prateek.doksp.ksp_learning

import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.prateek.doksp.DiProcessor

class ProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        //return DecoratorProcessor(environment.codeGenerator, environment.logger)
        return DiProcessor(environment.codeGenerator, environment.logger)
    }
}