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

package jm2pc.client.plugins;

import java.util.Hashtable;

public class PlugIns {

	private static Hashtable plugins;

	static {
		plugins = new Hashtable();
	}

	public static boolean containsCommand(String cmd) {

		if (plugins.contains(cmd))
			return true;
		else
			return false;
	}

	public static void addPlugin(int id, String cmd) {
		Integer key = new Integer(id);
		plugins.put(key, cmd);
	}

	public static String getPlugin(int id) {
		return (String) plugins.get(new Integer(id));
	}

	public static void removeAll() {
		plugins.clear();
	}

	public static int getPluginCount() {
		return plugins.size();
	}

}
