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

import javax.microedition.lcdui.*;

import jm2pc.client.*;

public class FormConfig extends Form implements CommandListener,
		ItemStateListener {

	private JM2PCMIDlet midlet;

	private Config cfg;
	private ConfigRepository repCfg;

	private TextField tfMaxBytesDowload;
	private ChoiceGroup cgColors;
	private ChoiceGroup cgOptions;

	private Command cmCancel;
	private Command cmSave;

	// quantidade média de memoria utilizada para processar imagens
	public static final int AUX_SUGGEST = 220436;

	public static final int ALERTS = 0;
	public static final int JPEG = 1;
	public static final int PLUGINS = 2;

	public static final int GRAY = 0;
	public static final int BITS_4 = 1;
	public static final int BITS_8 = 2;
	public static final int BITS_16 = 3;
	public static final int BITS_24 = 4;

	public FormConfig(JM2PCMIDlet midlet) {
		super(midlet.messages.getMessage("config"));
		this.midlet = midlet;

		repCfg = new ConfigRepository();

		cfg = repCfg.load();

		cgOptions = new ChoiceGroup(midlet.messages.getMessage("options"),
				Choice.MULTIPLE);
		cgOptions.append(midlet.messages.getMessage("alert"), null);
		cgOptions.append("JPEG encoding", null);
		cgOptions.append(midlet.messages.getMessage("plugin"), null);

		tfMaxBytesDowload = new TextField(midlet.messages
				.getMessage("maxBytesDownload"), String.valueOf(cfg
				.getMaxBytesDownload()), 6, TextField.NUMERIC);

		cgColors = new ChoiceGroup(midlet.messages.getMessage("colors"),
				Choice.EXCLUSIVE);
		cgColors.append(midlet.messages.getMessage("grayscale"), null);
		cgColors.append("4 bits", null);
		cgColors.append("8 bits", null);
		cgColors.append("16 bits", null);
		cgColors.append("24 bits", null);

		int bits = cfg.getBitsColor();

		int selected = 0;

		if (bits == 0)
			selected = GRAY;
		else if (bits == 4)
			selected = BITS_4;
		else if (bits == 8)
			selected = BITS_8;
		else if (bits == 16)
			selected = BITS_16;
		else if (bits == 24)
			selected = BITS_24;

		cgColors.setSelectedIndex(selected, true);
		cgOptions.setSelectedIndex(ALERTS, cfg.isAlertOk());
		cgOptions.setSelectedIndex(JPEG, cfg.isJpegImages());
		cgOptions.setSelectedIndex(PLUGINS, cfg.isReceivePlugins());

		cmSave = new Command(midlet.messages.getMessage("save"), Command.OK, 1);
		cmCancel = new Command(midlet.messages.getMessage("cancel"),
				Command.CANCEL, 2);

		this.append(cgOptions);
		this.append(tfMaxBytesDowload);
		this.append(cgColors);

		this.addCommand(cmSave);
		this.addCommand(cmCancel);
		this.setCommandListener(this);
		this.setItemStateListener(this);

	}

	public boolean validaDados() {
		String maxBytesImagem = tfMaxBytesDowload.getString();
		if (maxBytesImagem.trim().length() == 0) {
			midlet.showAlert(midlet.messages.getMessage("noMaxBytesDownload"),
					AlertType.ERROR, true, this);
			return false;
		} else if (cgColors.getSelectedIndex() == -1) {
			midlet.showAlert(midlet.messages.getMessage("noBitsImage"),
					AlertType.ERROR, true, this);
			return false;
		}

		cfg.setMaxBytesDownload(Integer.parseInt(maxBytesImagem));
		int selection = cgColors.getSelectedIndex();

		if (selection == GRAY)
			cfg.setBitsColor(0);
		else if (selection == BITS_4)
			cfg.setBitsColor(4);
		else if (selection == BITS_8)
			cfg.setBitsColor(8);
		else if (selection == BITS_16)
			cfg.setBitsColor(16);
		else if (selection == BITS_24)
			cfg.setBitsColor(24);

		cfg.setAlertOk(cgOptions.isSelected(ALERTS));
		cfg.setJpegImages(cgOptions.isSelected(JPEG));
		cfg.setReceivePlugins(cgOptions.isSelected(PLUGINS));

		return true;
	}

	public void commandAction(Command c, Displayable d) {
		if (c == cmSave) {
			if (validaDados()) {
				try {
					repCfg.save(cfg);
					midlet.getDisplayManager().popDisplayable();
				} catch (Exception e) {
					StringBuffer sb = new StringBuffer(midlet.messages
							.getMessage("errorSaveConfig"));
					sb.append(" [");
					sb.append(e.toString());
					sb.append("]");
					midlet
							.showAlert(sb.toString(), AlertType.ERROR, true,
									this);
				}
			}
		} else if (c == cmCancel) {
			midlet.getDisplayManager().popDisplayable();

		}
	}

	public void itemStateChanged(Item item) {
		if (item == tfMaxBytesDowload) {
			if (tfMaxBytesDowload.getString().equals("000")) {
				long sugestao = (Runtime.getRuntime().freeMemory() - AUX_SUGGEST) / 2;
				tfMaxBytesDowload.setString(String.valueOf(sugestao / 10));
			}
		}
	}

}
