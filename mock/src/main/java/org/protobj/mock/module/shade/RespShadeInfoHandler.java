package org.protobj.mock.module.shade;

import com.guangyu.cd003.projects.gs.module.shade.msg.RespShadeInfo;
import com.guangyu.cd003.projects.mock.net.MockConnect;
import com.guangyu.cd003.projects.mock.RespHandler;

public class RespShadeInfoHandler implements RespHandler<RespShadeInfo> {

	@Override
	public void handle(MockConnect connect, RespShadeInfo respMsg, int cmd) {
		//connect.LAST_RECV_MSGS.put(subCmd(), respMsg);

	}

	@Override
	public int subCmd() {
		return 1801;
	}
}
