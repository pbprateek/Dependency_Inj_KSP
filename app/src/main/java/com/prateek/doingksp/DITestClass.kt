package com.prateek.doingksp

import android.content.Context
import androidx.lifecycle.ViewModel
import com.prateek.doksp.inject_annotations.Inject
import com.prateek.doksp.inject_annotations.Singleton

@Singleton
class NetworkClient @Inject constructor()

class Repository1 @Inject constructor(private val net: NetworkClient)

class Repository2 @Inject constructor(private val net: NetworkClient)

class MyViewModel @Inject constructor(
    private val repo: Repository1,
    private val repository2: Repository2,
    private val context:Context
):ViewModel(){

    fun getPrateek():String{
        return "Prateek ${context.packageName}"
    }


}