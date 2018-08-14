package com.mboudraa.flow

internal class StateTransitionsHolder {

    private val transitionsMap = hashMapOf<State<*, *>, Any>()

    internal fun <INPUT, ACTION, STATE : State<INPUT, ACTION>> addTransition(state: STATE, transition: TransitionBuilder<INPUT, STATE>.(INPUT, ACTION) -> Transition<STATE, out State<*, *>>) {
        transitionsMap[state] = transition
    }

    internal fun <INPUT, ACTION, STATE : State<INPUT, ACTION>> getTransition(state: STATE): (TransitionBuilder<INPUT, STATE>.(INPUT, ACTION) -> Transition<STATE, out State<*, *>>)? {
        return transitionsMap[state] as? TransitionBuilder<INPUT, STATE>.(INPUT, ACTION) -> Transition<STATE, out State<*, *>>
    }
}