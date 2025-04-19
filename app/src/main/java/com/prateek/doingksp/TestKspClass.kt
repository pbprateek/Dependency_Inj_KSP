package com.prateek.doingksp

import com.prateek.doksp.ksp_learning.annotations.Decorator

@Decorator
interface TestKspClass {

    fun kspReturnType(): String
}