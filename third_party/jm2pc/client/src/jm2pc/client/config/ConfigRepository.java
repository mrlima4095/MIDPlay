/*
    JM2PC - Java Mobile to PC
    Copyright (C) 2005-2009 - Walter Alves Wanderley
    Recife - PE - Brazil

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package jm2pc.client.config;

import java.io.*;
import javax.microedition.rms.*;

public class ConfigRepository {

	private static final String RS_CONFIG_NAME = "jm2pc-cfg";

	private RecordStore rs;

	public ConfigRepository() {

	}

	public void open() throws RecordStoreException {
		rs = RecordStore.openRecordStore(RS_CONFIG_NAME, true);
	}

	public void save(Config cfg) throws IOException, RecordStoreException {

		ByteArrayOutputStream bOut = new ByteArrayOutputStream();
		DataOutputStream dataOut = new DataOutputStream(bOut);

		dataOut.writeBoolean(cfg.isAlertOk());
		dataOut.writeBoolean(cfg.isJpegImages());
		dataOut.writeBoolean(cfg.isReceivePlugins());

		dataOut.writeInt(cfg.getMaxBytesDownload());
		dataOut.writeInt(cfg.getBitsColor());

		byte[] dados = bOut.toByteArray();

		open();
		try {
			if (rs.getNumRecords() == 0)
				rs.addRecord(dados, 0, dados.length);
			else {
				rs.setRecord(1, dados, 0, dados.length);
			}
		} finally {
			close();
		}

	}

	public Config load() {
		Config cfg = new Config();

		try {
			open();
			if (rs.getNumRecords() == 0)
				return cfg;
			else {
				ByteArrayInputStream bIn = new ByteArrayInputStream(rs
						.getRecord(1));
				DataInputStream dataIn = new DataInputStream(bIn);

				cfg.setAlertOk(dataIn.readBoolean());
				cfg.setJpegImages(dataIn.readBoolean());
				cfg.setReceivePlugins(dataIn.readBoolean());

				cfg.setMaxBytesDownload(dataIn.readInt());
				cfg.setBitsColor(dataIn.readInt());

				return cfg;
			}
		} catch (Exception e) {
			return cfg;
		} finally {
			try {
				close();
			} catch (RecordStoreException e) {
			}

		}

	}

	public void close() throws RecordStoreException {
		rs.closeRecordStore();
	}

}
