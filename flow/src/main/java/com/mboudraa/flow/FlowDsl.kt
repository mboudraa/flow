package com.mboudraa.flow

typealias SideEffects = (flow: Flow, transition: Pair<State<*, *>, State<*, *>>, action: Any) -> Unit

class FlowBuilder {

    internal var defaultState: DefaultState<*>? = null
        private set

    internal val transitionsHolder = StateTransitionsHolder()
    internal var sideEffects: SideEffects? = null
        private set

    fun <INPUT> startWith(state: State<INPUT, *>, data: INPUT) {
        defaultState = DefaultState(state, data)
    }


    fun <INPUT, ACTION, STATE : State<INPUT, ACTION>> forState(state: STATE, transition: TransitionBuilder<INPUT, STATE>.(INPUT, ACTION) -> Transition<STATE, out State<*, *>>) {
        transitionsHolder.addTransition(state, transition)
    }

    fun onTransition(f: SideEffects) {
        sideEffects = f
    }

    internal data class DefaultState<INPUT>(val state: State<INPUT, *>, val input: INPUT)
}


class TransitionBuilder<INPUT, STATE : State<INPUT, *>>(private val from: STATE) {

    fun <DEST_INPUT, DEST_STATE : State<DEST_INPUT, *>> goto(state: DEST_STATE): EmptyTransition<INPUT, STATE, DEST_INPUT, DEST_STATE> {
        return EmptyTransition(from, state)
    }

    fun <DEST_INPUT, DEST_STATE : State<DEST_INPUT, *>> goBackTo(state: DEST_STATE): BackTransition<STATE, DEST_INPUT, DEST_STATE> {
        return BackTransition(from, state)
    }

    val stay
        get() = goto(from)
}

class EmptyTransition<FROM_INPUT, FROM_STATE : State<FROM_INPUT, *>, TO_INPUT, TO_STATE : State<TO_INPUT, *>>(private val from: FROM_STATE, private val to: TO_STATE) {

    infix fun using(data: TO_INPUT): ActionTransition<FROM_INPUT, FROM_STATE, TO_INPUT, TO_STATE> {
        return ActionTransition(from, to, data)
    }
}


sealed class Transition<FROM_STATE : State<*, *>, TO_STATE : State<*, *>>(val from: FROM_STATE, val to: TO_STATE) {
    abstract fun perform(flow: Flow)
}

class ActionTransition<FROM_INPUT, FROM_STATE : State<FROM_INPUT, *>, TO_INPUT, TO_STATE : State<TO_INPUT, *>>(from: FROM_STATE, to: TO_STATE, val data: TO_INPUT) : Transition<FROM_STATE, TO_STATE>(from, to) {

    infix fun replying(data: FROM_INPUT): ReplyingTransition<FROM_INPUT, FROM_STATE, TO_INPUT, TO_STATE> {
        return ReplyingTransition(from, to, data, this.data)
    }

    override fun perform(flow: Flow) {
        flow.setCurrentState(to, data)
    }

}

class ReplyingTransition<FROM_INPUT, FROM_STATE : State<FROM_INPUT, *>, TO_INPUT, TO_STATE : State<TO_INPUT, *>>(from: FROM_STATE, to: TO_STATE, val fromData: FROM_INPUT, val toData: TO_INPUT) : Transition<FROM_STATE, TO_STATE>(from, to) {

    override fun perform(flow: Flow) {
        flow.setStateData(from, fromData)
        flow.setCurrentState(to, toData)
    }

}

class BackTransition<FROM_STATE : State<*, *>, TO_INPUT, TO_STATE : State<TO_INPUT, *>>(from: FROM_STATE, to: TO_STATE) : Transition<FROM_STATE, TO_STATE>(from, to) {
    override fun perform(flow: Flow) {
        flow.setCurrentState(to, flow.getStateData(to))
    }

}