package com.mboudraa.flow


typealias OnStateChangeListener = (state: State<*, *>, flow: Flow) -> Unit


abstract class Flow(body: FlowBuilder.() -> Unit) {

    private val dataStore = DataStore()
    private val stateChangeListeners = arrayListOf<OnStateChangeListener>()
    private val transitionListeners = arrayListOf<OnTransitionListener>()
    private val transitionsHolder: StateTransitionsHolder
    private val sideEffects: SideEffects?

    lateinit var currentState: State<*, *>
        private set


    init {
        val flowBuilder = FlowBuilder().apply(body)

        flowBuilder.defaultState?.let { (state, data) ->
            setCurrentState(state as State<Any?, *>, data)
        } ?: throw DefaultStateMissingException("Default state is missing for flow $this")

        transitionsHolder = flowBuilder.transitionsHolder
        sideEffects = flowBuilder.sideEffects
    }

    fun addOnStateChangeListener(listener: OnStateChangeListener): Boolean {
        listener(currentState, this)
        return stateChangeListeners.add(listener)
    }

    fun removeOnStateChangeListener(listener: OnStateChangeListener): Boolean {
        return stateChangeListeners.remove(listener)
    }

    fun addOnTransitionListener(listener: OnTransitionListener): Boolean {
        return transitionListeners.add(listener)
    }

    fun removeOnTransitionListener(listener: OnTransitionListener): Boolean {
        return transitionListeners.remove(listener)
    }

    internal fun <INPUT, ACTION : Any> dispatchAction(state: State<INPUT, ACTION>, action: ACTION): Boolean {
        if (state != currentState) return false
        val previousState = currentState

        val transition = transitionsHolder.getTransition(state)?.invoke(TransitionBuilder(state), getStateData(state) as INPUT, action)
        transition?.run {
            perform(this@Flow)
            if (previousState != currentState) {
                transitionListeners.forEach { it.onTransition(previousState to currentState, this@Flow) }
            }
        } ?: throw TransitionMissingException("transition is missing for action $action in state $state")


        sideEffects?.invoke(this, transition.from to transition.to, action)
        return true
    }

    internal fun <INPUT> setCurrentState(state: State<INPUT, *>, data: INPUT?) {
        setStateData(state, data)
        currentState = state
        stateChangeListeners.forEach { it(currentState, this) }
    }

    internal fun <INPUT> getStateData(state: State<INPUT, *>): INPUT? {
        return dataStore.getData(state)
    }

    internal fun <INPUT, STATE : State<INPUT, *>> setStateData(state: STATE, data: INPUT?) {
        data?.let { dataStore.putData(state, it) }
    }

    private class DataStore {

        private val store = hashMapOf<String, Any?>()

        fun <DATA> getData(state: State<DATA, *>): DATA? {
            return store[state.javaClass.canonicalName] as? DATA
        }

        fun <DATA, STATE : State<DATA, *>> putData(state: STATE, data: DATA) {
            store[state.javaClass.canonicalName] = data
        }
    }

    interface OnTransitionListener {
        fun onTransition(transition: Pair<State<*, *>, State<*, *>>, flow: Flow)
    }
}

inline fun <reified STATE : State<*, *>> Flow.currentStateAs(): STATE? = currentState as? STATE