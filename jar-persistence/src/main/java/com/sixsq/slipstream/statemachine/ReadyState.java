package com.sixsq.slipstream.statemachine;

import com.sixsq.slipstream.persistence.Parameter;
import com.sixsq.slipstream.persistence.ParameterCategory;
import com.sixsq.slipstream.persistence.Run;
import com.sixsq.slipstream.persistence.RunParameter;
import com.sixsq.slipstream.persistence.RunType;
import com.sixsq.slipstream.persistence.UserParameter;

/*
 * +=================================================================+
 * SlipStream Server (WAR)
 * =====
 * Copyright (C) 2013 SixSq Sarl (sixsq.com)
 * =====
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * -=================================================================-
 */


public class ReadyState extends SynchronizedState {

    public ReadyState(ExtrinsicState extrinsicState) {
        super(extrinsicState);
        
        Run run = extrinsicState.getRun();
        
        if (shouldStayInReady(run)) {
        	nextState = States.Ready;
        } else { 
        	nextState = States.Finalizing;
        }
    }

	private boolean shouldStayInReady(Run run) {
		String key = Parameter.constructKey(ParameterCategory.General.toString(),
				UserParameter.KEY_ON_SUCCESS_RUN_FOREVER);
		RunParameter onSuccessKeepRunning = run.getParameter(key);
		
		key = Parameter.constructKey(ParameterCategory.General.toString(),
				UserParameter.KEY_ON_ERROR_RUN_FOREVER);
		RunParameter onErrorKeepRunning = run.getParameter(key);
		
		return run.getType() == RunType.Run || 
				(onSuccessKeepRunning != null && onSuccessKeepRunning.isTrue()) ||
				(onErrorKeepRunning != null && onErrorKeepRunning.isTrue());
	}
	    
    @Override
    public States getState() {
        return States.Ready;
    }

}
