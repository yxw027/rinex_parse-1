package com.RINEX_parser.fileParser;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;

import com.RINEX_parser.models.ObservationMsg;
import com.RINEX_parser.models.SatelliteModel;

public class ObservationRNX {

	public static ArrayList<ObservationMsg> rinex_obsv_process(String path) {
		File file = new File(path);
		ArrayList<ObservationMsg> ObsvMsgs = new ArrayList<ObservationMsg>();
		try {
			Scanner input = new Scanner(file);

			input.useDelimiter("HEADER");

			Scanner header = new Scanner(input.next());

			double[] ECEF_XYZ = new double[3];
			ArrayList<String> obs_types = new ArrayList<String>();
			while (header.hasNextLine()) {
				// Remove leading and trailing whitespace, as split method adds them
				String line = header.nextLine().trim();
				if (line.contains("APPROX POSITION XYZ")) {
					ECEF_XYZ = Arrays.stream(line.split("\\s+")).limit(3).mapToDouble(x -> Double.parseDouble(x))
							.toArray();

				} else if (line.contains("SYS / # / OBS TYPES")) {
					obs_types.addAll(Arrays.asList(line.replaceAll("SYS / # / OBS TYPES", "").split("\\s+")));
				}
			}

			int GPSindex = obs_types.indexOf("G");
			int C1C_index = 0;
			int S1C_index = 0;
			for (int i = 0; i < Integer.parseInt(obs_types.get(GPSindex + 1)); i++) {
				String type = obs_types.get(GPSindex + 2 + i).trim();

				if (type.equals("C1C")) {
					C1C_index = i;
				} else if (type.equals("S1C")) {
					S1C_index = i;
				}
			}

			String[] obsv_msgs = input.next().trim().split(">");
			for (String msg : obsv_msgs) {
				if (msg.isBlank()) {
					continue;
				}

				ArrayList<SatelliteModel> SV = new ArrayList<SatelliteModel>();
				ObservationMsg Msg = new ObservationMsg();
				msg = msg.trim();

				String[] msgTokens = msg.split("\\R+");
				for (int i = 1; i < msgTokens.length; i++) {
					String token = msgTokens[i];
					String SVID = token.substring(0, 3);
					if (token.trim().charAt(0) == 'G') {
						token = token.substring(3);
						String[] tokenArray = token.split("(?<=\\G.{16})");
						tokenArray = Arrays.stream(tokenArray).map(x -> new String(x.trim())).toArray(String[]::new);

						SV.add(new SatelliteModel(SVID, tokenArray[C1C_index], tokenArray[S1C_index]));
					}
				}

				Msg.set_ECEF_XYZ(ECEF_XYZ);
				Msg.set_RxTime(msgTokens[0].trim().split("\\s+"));
				Msg.setObsvSat(SV);

				ObsvMsgs.add(Msg);

			}

		} catch (

		FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return ObsvMsgs;
	}

}
