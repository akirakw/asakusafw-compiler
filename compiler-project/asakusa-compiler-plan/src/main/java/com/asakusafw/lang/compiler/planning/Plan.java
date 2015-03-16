package com.asakusafw.lang.compiler.planning;

import java.util.Set;

import com.asakusafw.lang.compiler.common.AttributeContainer;

/**
 * Represents an execution plan.
 * <p>
 * Each {@link Plan} object must satisfy following invariants:
 * </p>
 * <ol>
 * <li> each sub-plan satisfies its {@link SubPlan invariants} </li>
 * <li> dependency graph of the sub-plans is acyclic </li>
 * </ol>
 * @see PlanBuilder
 */
public interface Plan extends AttributeContainer {

    /**
     * Returns sub-plans in this execution plan.
     * @return sub-plans
     */
    Set<? extends SubPlan> getElements();
}