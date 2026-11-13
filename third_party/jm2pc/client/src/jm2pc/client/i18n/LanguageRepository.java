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

package jm2pc.client.i18n;

import java.io.*;
import javax.microedition.rms.*;

public class LanguageRepository {

	private static final String RS_LANGUAGE_NAME = "jm2pc-i18n";

	private RecordStore rs;

	public LanguageRepository() {

	}

	private void open() throws RecordStoreException {
		rs = RecordStore.openRecordStore(RS_LANGUAGE_NAME, true);
	}

	public void salvarLanguage(String language) throws IOException,
			RecordStoreException {

		ByteArrayOutputStream bOut = new ByteArrayOutputStream();
		DataOutputStream dataOut = new DataOutputStream(bOut);

		dataOut.writeUTF(language);

		byte[] data = bOut.toByteArray();

		open();
		try {
			if (rs.getNumRecords() == 0)
				rs.addRecord(data, 0, data.length);
			else {
				rs.setRecord(1, data, 0, data.length);
			}
		} finally {
			close();
		}

	}

	public String loadLanguage() {

		try {
			open();
			if (rs.getNumRecords() == 0)
				return null;
			else {
				ByteArrayInputStream bIn = new ByteArrayInputStream(rs
						.getRecord(1));
				DataInputStream dataIn = new DataInputStream(bIn);

				return dataIn.readUTF();

			}
		} catch (Exception e) {
			return null;
		} finally {
			close();
		}
	}

	private void close() {
		try {
			rs.closeRecordStore();
		} catch (RecordStoreException e) {
			System.out.println(e.getMessage());
		}
	}

}