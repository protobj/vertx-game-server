package org.protobj.mock.module.leaguebuilding;

import com.guangyu.cd003.projects.gs.module.leaguebuilding.msg.RespLeagueBuildingOp;
import com.guangyu.cd003.projects.mock.net.MockConnect;
import com.guangyu.cd003.projects.mock.RespHandler;

public class RespLeagueBuildingOpHandler implements RespHandler<RespLeagueBuildingOp> {

	@Override
	public void handle(MockConnect connect, RespLeagueBuildingOp respMsg, int cmd) {
		//connect.LAST_RECV_MSGS.put(subCmd(), respMsg);

	}

	@Override
	public int subCmd() {
		return 2501;
	}
}
