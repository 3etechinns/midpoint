/*
 * Copyright (c) 2010-2017 Evolveum
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.evolveum.midpoint.model.impl.lens.projector.policy;

import com.evolveum.midpoint.model.api.context.EvaluatedPolicyRule;
import com.evolveum.midpoint.model.impl.lens.EvaluatedAssignmentImpl;
import com.evolveum.midpoint.model.impl.lens.LensContext;
import com.evolveum.midpoint.prism.delta.DeltaSetTriple;
import com.evolveum.midpoint.xml.ns._public.common.common_3.FocusType;
import org.jetbrains.annotations.NotNull;

/**
 * @author mederly
 */
public class AssignmentPolicyRuleEvaluationContext<F extends FocusType> extends PolicyRuleEvaluationContext<F> {

	@NotNull public final EvaluatedAssignmentImpl<F> evaluatedAssignment;
	public final boolean inPlus;
	public final boolean inZero;
	public final boolean inMinus;
	public final boolean isDirect;
	public final DeltaSetTriple<EvaluatedAssignmentImpl<F>> evaluatedAssignmentTriple;

	public AssignmentPolicyRuleEvaluationContext(@NotNull EvaluatedPolicyRule policyRule,
			@NotNull EvaluatedAssignmentImpl<F> evaluatedAssignment, boolean inPlus, boolean inZero,
			boolean inMinus, boolean isDirect, LensContext<F> context,
			DeltaSetTriple<EvaluatedAssignmentImpl<F>> evaluatedAssignmentTriple) {
		super(policyRule, context);
		this.evaluatedAssignment = evaluatedAssignment;
		this.inPlus = inPlus;
		this.inZero = inZero;
		this.inMinus = inMinus;
		this.isDirect = isDirect;
		this.evaluatedAssignmentTriple = evaluatedAssignmentTriple;
	}

	@Override
	public void triggerRule() {
		evaluatedAssignment.triggerRule(policyRule, triggers);
	}
}
