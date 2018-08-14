package com.mboudraa.flow

abstract class State<INPUT, ACTION : Any> {

    fun getData(flow: Flow): INPUT = flow.getStateData(this) as INPUT

    fun dispatchAction(flow: Flow, action: ACTION) = flow.dispatchAction(this, action)

}