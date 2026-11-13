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

package jm2pc.client.servers;

import java.io.*;
import java.util.Vector;
import javax.microedition.rms.*;

public class ServerRepository {

	private static final String RS_SERVIDORES_NAME = "jm2pc-servers";

	private RecordStore rs;
	private RecordEnumeration re;
	private RecordComparator comparator;

	public ServerRepository() {
		comparator = new Comparator();
	}

	public void open() throws RecordStoreException {
		rs = RecordStore.openRecordStore(RS_SERVIDORES_NAME, true);
		re = rs.enumerateRecords(null, comparator, true);
	}

	public void saveServer(Server server) throws IOException,
			RecordStoreException {

		ByteArrayOutputStream bOut = new ByteArrayOutputStream();
		DataOutputStream dataOut = new DataOutputStream(bOut);

		dataOut.writeInt(server.getType());
		dataOut.writeUTF(server.getDescription());
		dataOut.writeUTF(server.getAddress());
		dataOut.writeInt(server.getPort());
		dataOut.writeUTF(server.getPassword()); // futuramente desenvolver
		// criptografia de senha

		byte[] dados = bOut.toByteArray();

		int id = server.getId();

		open();
		try {
			if (id == 0)
				rs.addRecord(dados, 0, dados.length);
			else {
				rs.setRecord(id, dados, 0, dados.length);
			}
		} finally {
			close();
		}

	}

	public void deleteServer(Server servidor) throws RecordStoreException {
		open();
		rs.deleteRecord(servidor.getId());
		close();
	}

	private Server getServer(int idServer, byte[] dados) throws IOException,
			RecordStoreException {

		Server server = new Server();

		ByteArrayInputStream bIn = new ByteArrayInputStream(dados);
		DataInputStream dataIn = new DataInputStream(bIn);

		server.setType(dataIn.readInt());
		server.setDescription(dataIn.readUTF());
		server.setAddress(dataIn.readUTF());
		server.setPort(dataIn.readInt());
		server.setPassword(dataIn.readUTF());

		server.setId(idServer);

		return server;

	}

	public Vector loadServers() throws IOException, RecordStoreException {
		Vector servers = new Vector();

		open();
		try {
			while (re.hasNextElement()) {
				int id = re.nextRecordId();
				servers.addElement(getServer(id, rs.getRecord(id)));

			}
		} finally {
			close();
		}
		return servers;
	}

	public void close() throws RecordStoreException {
		re.destroy();
		rs.closeRecordStore();
	}

	class Comparator implements RecordComparator {

		private ByteArrayInputStream bIn;
		private DataInputStream dataIn;

		public int compare(byte[] r1, byte[] r2) {
			String dsc1, dsc2;

			try {
				bIn = new ByteArrayInputStream(r1);
				dataIn = new DataInputStream(bIn);

				dataIn.readInt();
				dsc1 = dataIn.readUTF().toLowerCase();

				bIn = new ByteArrayInputStream(r2);
				dataIn = new DataInputStream(bIn);

				dataIn.readInt();
				dsc2 = dataIn.readUTF().toLowerCase();

				int resultado = dsc1.compareTo(dsc2);
				if (resultado == 0)
					return RecordComparator.EQUIVALENT;
				else if (resultado < 0)
					return RecordComparator.PRECEDES;
				else
					return RecordComparator.FOLLOWS;
			} catch (IOException e) {
				return RecordComparator.EQUIVALENT;
			}

		}
	}

}
