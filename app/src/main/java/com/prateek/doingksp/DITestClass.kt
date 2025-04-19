package com.prateek.doingksp

import androidx.lifecycle.ViewModel
import com.prateek.doksp.inject_annotations.Inject
import com.prateek.doksp.inject_annotations.Singleton

@Singleton
class NetworkClient @Inject constructor()

class Repository1 @Inject constructor(private val net: NetworkClient)

class Repository2 @Inject constructor(private val net: NetworkClient)

class MyViewModel @Inject constructor(
    private val repo: Repository1,
    private val repository2: Repository2
):ViewModel(){

    init {

    }

    fun getPrateek():String{
        return "Prateek"
    }


}