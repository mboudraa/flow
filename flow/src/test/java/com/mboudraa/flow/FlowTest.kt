package com.mboudraa.flow

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FlowTest {

    object State1 : State<String?, State1.Action>() {
        data class Action(val data: Int)
    }

    object State2 : State<Int, State2.Action>() {
        sealed class Action {
            data class Action1(val data: Int) : Action()
            object Back : Action()
        }
    }

    object State3 : State<Int, State3.Action>() {
        sealed class Action {
            data class Action1(val data: Int) : Action()
        }
    }

    object State4 : State<Unit, State4.Action>() {
        sealed class Action {
            data class Action1(val data: Int) : Action()
        }
    }

    @Test fun `should set default state with default data`() {
        val expectedStateData = 10
        val flow = object : Flow({
            startWith(State2, expectedStateData)
        }) {}

        assertThat(flow.currentState).isEqualTo(State2)
        assertThat(flow.currentStateAs<State2>()?.getData(flow)).isEqualTo(expectedStateData)
    }

    @Test(expected = DefaultStateMissingException::class)
    fun `should throw DefaultStateMissingException when default state is missing`() {
        object : Flow({}) {}
    }

    @Test(expected = TransitionMissingException::class)
    fun `should throw TransitionMissingException when action is called without transition associated`() {
        val flow = object : Flow({
            startWith(State1, "data")
        }) {}
        flow.currentStateAs<State1>()?.dispatchAction(flow, State1.Action(1))
    }

    @Test fun `should go to next state with the right data when an action is dispatched`() {
        val flow = object : Flow({
            startWith(State1, "data")
            forState(State1) { _, action -> goto(State2) using action.data }
        }) {}

        flow.currentStateAs<State1>()?.dispatchAction(flow, State1.Action(1))

        assertThat(flow.currentState).isEqualTo(State2)
        assertThat(flow.currentState.getData(flow)).isEqualTo(1)
    }

    @Test fun `should allow to go to State with Unit as input without using 'using' keyword`() {
        val flow = object : Flow({
            startWith(State1, "data")
            forState(State1) { _, action -> goto(State4)}
        }) {}

        flow.currentStateAs<State1>()?.dispatchAction(flow, State1.Action(1))

        assertThat(flow.currentState).isEqualTo(State4)
    }

    @Test fun `should stay in the same state when using stay`() {
        val flow = object : Flow({
            startWith(State1, "data")
            forState(State1) { _, _ -> stay using "data2" }
        }) {}

        flow.currentStateAs<State1>()?.dispatchAction(flow, State1.Action(1))

        assertThat(flow.currentState).isEqualTo(State1)
        assertThat(flow.currentState.getData(flow)).isEqualTo("data2")
    }

    @Test fun `should allow nullable input for state`() {
        val flow = object : Flow({
            startWith(State1, null)

            forState(State1) { _, action -> goto(State2) using action.data }
        }) {}


        flow.currentStateAs<State1>()?.dispatchAction(flow, State1.Action(0))
        assertThat(flow.currentState).isEqualTo(State2)
    }

    @Test fun `should go back and retrieve data from previous state`() {
        val flow = object : Flow({
            startWith(State1, "default data")

            forState(State1) { _, action -> goto(State2) using action.data }
            forState(State2) { _, action ->
                when (action) {
                    is State2.Action.Back -> goBackTo(State1)
                    else -> TODO()
                }
            }
        }) {}

        flow.currentStateAs<State1>()!!.dispatchAction(flow, State1.Action(0))
        flow.currentStateAs<State2>()!!.dispatchAction(flow, State2.Action.Back)

        assertThat(flow.currentState).isEqualTo(State1)
        assertThat(flow.currentState.getData(flow)).isEqualTo("default data")
    }

    @Test fun `should update from state data when using replying`() {
        val flow = object : Flow({
            startWith(State1, "default data")
            forState(State1) { _, action -> goto(State2) using action.data replying "data replaced" }
        }) {}

        flow.currentStateAs<State1>()!!.dispatchAction(flow, State1.Action(0))

        assertThat(flow.currentState).isEqualTo(State2)
        assertThat(flow.getStateData(State1)).isEqualTo("data replaced")

    }

    @Test fun `should listen transition in Flow`() {
        lateinit var expectedTransition: Pair<State<*, *>, State<*, *>>
        lateinit var expectedAction: Any

        val flow = object : Flow({
            startWith(State1, "default data")

            forState(State1) { _, action -> goto(State2) using action.data }

            onTransition { _, transition, action ->
                expectedTransition = transition
                expectedAction = action
            }

        }) {}

        flow.currentStateAs<State1>()!!.dispatchAction(flow, State1.Action(0))

        assertThat(expectedTransition).isEqualTo(State1 to State2)
        assertThat(expectedAction).isInstanceOf(State1.Action::class.java)
    }

    @Test fun `should forward transition to transition listeners`() {
        val flow = object : Flow({
            startWith(State1, "default data")

            forState(State1) { _, action -> goto(State2) using action.data }
            forState(State2) { _, action ->
                when (action) {
                    is State2.Action.Action1 -> goto(State3) using action.data
                    FlowTest.State2.Action.Back -> TODO()
                }
            }

        }), Flow.OnTransitionListener {
            val transitions = arrayListOf<Pair<State<*, *>,State<*, *>>>()
            override fun onTransition(transition: Pair<State<*, *>, State<*, *>>, flow: Flow) {
                transitions.add(transition)
            }
        }
        flow.addOnTransitionListener(flow)

        flow.currentStateAs<State1>()!!.dispatchAction(flow, State1.Action(0))
        flow.currentStateAs<State2>()!!.dispatchAction(flow, State2.Action.Action1(0))

        assertThat(flow.transitions).containsExactly(State1 to State2, State2 to State3).inOrder()
    }

    @Test fun `should not forward transition to transition listeners when stay in same state`() {
        val flow = object : Flow({
            startWith(State1, "default data")

            forState(State1) { _, action -> goto(State2) using action.data }
            forState(State2) { _, action ->
                when (action) {
                    is State2.Action.Action1 -> stay using action.data
                    FlowTest.State2.Action.Back -> goBackTo(State1)
                }
            }

        }), Flow.OnTransitionListener {
            val transitions = arrayListOf<Pair<State<*, *>,State<*, *>>>()
            override fun onTransition(transition: Pair<State<*, *>, State<*, *>>, flow: Flow) {
                transitions.add(transition)
            }
        }

        flow.addOnTransitionListener(flow)

        flow.currentStateAs<State1>()!!.dispatchAction(flow, State1.Action(0))
        flow.currentStateAs<State2>()!!.dispatchAction(flow, State2.Action.Action1(0))
        flow.currentStateAs<State2>()!!.dispatchAction(flow, State2.Action.Back)

        assertThat(flow.transitions).containsExactly(State1 to State2, State2 to State1).inOrder()
    }

    @Test fun `should remove OnTransitionListener`() {
        val flow = object : Flow({
            startWith(State1, "default data")

            forState(State1) { _, action -> goto(State2) using action.data }
            forState(State2) { _, action ->
                when (action) {
                    is State2.Action.Action1 -> goto(State3) using action.data
                    FlowTest.State2.Action.Back -> TODO()
                }
            }

        }), Flow.OnTransitionListener {
            val transitions = arrayListOf<Pair<State<*, *>,State<*, *>>>()
            override fun onTransition(transition: Pair<State<*, *>, State<*, *>>, flow: Flow) {
                transitions.add(transition)
            }
        }
        flow.addOnTransitionListener(flow)
        flow.currentStateAs<State1>()!!.dispatchAction(flow, State1.Action(0))
        flow.removeOnTransitionListener(flow)
        flow.currentStateAs<State2>()!!.dispatchAction(flow, State2.Action.Action1(0))
        assertThat(flow.transitions).containsExactly(State1 to State2).inOrder()
    }

    @Test fun `should listen states change `() {
        val flow = object : Flow({
            startWith(State1, "default data")

            forState(State1) { _, action -> goto(State2) using action.data }
            forState(State2) { _, action ->
                when (action) {
                    is State2.Action.Action1 -> stay using action.data
                    FlowTest.State2.Action.Back -> goBackTo(State1)
                }
            }

        }), OnStateChangeListener {

            val states = arrayListOf<State<*, *>>()

            override fun invoke(state: State<*, *>, flow: Flow) {
                states.add(state)
            }
        }
        flow.addOnStateChangeListener(flow)

        flow.currentStateAs<State1>()!!.dispatchAction(flow, State1.Action(0))
        flow.currentStateAs<State2>()!!.dispatchAction(flow, State2.Action.Action1(0))
        flow.currentStateAs<State2>()!!.dispatchAction(flow, State2.Action.Back)

        assertThat(flow.states).containsExactly(State1, State2, State2, State1).inOrder()
    }

    @Test fun `should get the current state when add listener`() {
        val flow = object : Flow({
            startWith(State1, "default data")

            forState(State1) { _, action -> goto(State2) using action.data }

        }), OnStateChangeListener {

            val states = arrayListOf<State<*, *>>()

            override fun invoke(state: State<*, *>, flow: Flow) {
                states.add(state)
            }
        }

        flow.currentStateAs<State1>()!!.dispatchAction(flow, State1.Action(0))

        flow.addOnStateChangeListener(flow)

        assertThat(flow.states).containsExactly(State2)
    }

    @Test fun `should remove OnStateChangeListener`() {
        val flow = object : Flow({
            startWith(State1, "default data")

            forState(State1) { _, action -> goto(State2) using action.data }
            forState(State2) { _, action ->
                when (action) {
                    is State2.Action.Action1 -> stay using action.data
                    FlowTest.State2.Action.Back -> goBackTo(State1)
                }
            }

        }), OnStateChangeListener {

            val states = arrayListOf<State<*, *>>()

            override fun invoke(state: State<*, *>, flow: Flow) {
                states.add(state)
            }
        }

        flow.addOnStateChangeListener(flow)
        flow.currentStateAs<State1>()!!.dispatchAction(flow, State1.Action(0))
        flow.currentStateAs<State2>()!!.dispatchAction(flow, State2.Action.Action1(0))
        flow.removeOnStateChangeListener(flow)

        flow.currentStateAs<State2>()!!.dispatchAction(flow, State2.Action.Back)

        assertThat(flow.states).containsExactly(State1, State2, State2).inOrder()
    }

}