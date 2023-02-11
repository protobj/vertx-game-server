package org.protobj.mock.module.licence;

import com.guangyu.cd003.projects.gs.module.licence.msg.RespLicenceInfo;
import com.guangyu.cd003.projects.mock.net.MockConnect;
import com.guangyu.cd003.projects.mock.RespHandler;

public class RespLicenceInfoHandler implements RespHandler<RespLicenceInfo> {

	@Override
	public void handle(MockConnect connect, RespLicenceInfo respMsg, int cmd) {
		connect.LAST_RECV_MSGS.put(subCmd(), respMsg);

	}

	@Override
	public int subCmd() {
		return 5601;
	}
}