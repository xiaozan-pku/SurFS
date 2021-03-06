/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package com.surfs.storage.block.dao.mapper;

import java.sql.ResultSet;
import java.sql.SQLException;

import com.surfs.storage.block.model.BlockUserTarget;
import com.surfs.storage.common.datasource.jdbc.mapper.RowMapper;

public class BlockUserTargetMapper implements RowMapper<BlockUserTarget> {

	@Override
	public BlockUserTarget mapRow(ResultSet rs) throws SQLException {
		// TODO Auto-generated method stub
		BlockUserTarget blockUserTarget = new BlockUserTarget();
		int columnCount = rs.getMetaData().getColumnCount();
		for(int i=1;i<=columnCount;i++){
			blockUserTarget.setUserTargetId(rs.getInt("userTargetId"));
			blockUserTarget.setTarget(rs.getString("target"));
			blockUserTarget.setUserId(rs.getInt("userId"));
		}
		return blockUserTarget;
	}

}
