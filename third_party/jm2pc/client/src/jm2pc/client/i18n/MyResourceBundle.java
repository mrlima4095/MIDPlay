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

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;

import java.util.Hashtable;

public class MyResourceBundle extends Hashtable {

	private InputStream in;

	private ByteArrayOutputStream bStream;

	private static final String PATH = "/jm2pc/i18n/messages_";
	private static final String DEFAULT = "/jm2pc/i18n/messages_en";

	public MyResourceBundle() {
		super();
		bStream = new ByteArrayOutputStream();
	}

	public void loadMessages(String locale) {
		if (locale == null) {
			locale = System.getProperty("microedition.locale");

			if (locale == null || locale.length() == 0)
				locale = "en";
			else if (locale.length() > 2) {
				locale = locale.substring(0, 2);
			}
		}

		clear();
		StringBuffer sb = new StringBuffer(PATH);
		sb.append(locale);
		in = getClass().getResourceAsStream(sb.toString());
		if (in == null)
			in = getClass().getResourceAsStream(DEFAULT);
		try {
			parseMessages();

		} catch (IOException e) {
			System.out.println(e.getMessage());
		} finally {
			try {
				in.close();
			} catch (IOException ioe) {
			}
		}

	}

	public void parseMessages() throws IOException {
		String line;
		while ((line = readLine()) != null)
			parseLine(line);
	}

	public void parseLine(String line) {
		int i = line.indexOf("=");
		if (i != -1 && i < line.length() - 1) {
			String key = line.substring(0, i);
			String value = line.substring(i + 1);

			put(key, value);
		}

	}

	public String readLine() throws IOException {
		String line = null;

		int i;
		while ((i = in.read()) != -1) {
			if (i == 13)
				break;
			else
				bStream.write(i);
		}
		in.read(); // descartando o caracter '\n'
		line = bStream.toString();

		bStream.reset();

		if (line.length() < 3)
			return null;
		else {
			return line;
		}

	}

	public String getMessage(String key) {
		String msg = (String) get(key);
		if (msg == null || msg.length() == 0)
			return key;
		else
			return msg;
	}

}
