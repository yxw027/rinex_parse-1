package com.RINEX_parser.fileParser;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Scanner;

import com.RINEX_parser.constants.Constellation;
import com.RINEX_parser.models.Observable;
import com.RINEX_parser.models.ObservationMsg;

public class ObservationRNX {

	public static ArrayList<ObservationMsg> rinex_obsv_process(String path, boolean useSNX) {
		File file = new File(path);
		ArrayList<ObservationMsg> ObsvMsgs = new ArrayList<ObservationMsg>();
		try {
			Scanner input = new Scanner(file);

			input.useDelimiter("HEADER");

			Scanner header = new Scanner(input.next());

			double[] ECEF_XYZ = new double[3];
			String siteCode = null;
			HashMap<Character, HashSet<String>> availObs = new HashMap<Character, HashSet<String>>();
			HashMap<Character, HashMap<String, Integer>> type_index_map = new HashMap<Character, HashMap<String, Integer>>();
			while (header.hasNextLine()) {

				// Remove leading and trailing whitespace, as split method adds them
				String line = header.nextLine().trim();
				if (line.contains("MARKER NAME")) {
					siteCode = line.split("\\s+")[0].trim().substring(0, 4);

				} else if (line.contains("APPROX POSITION XYZ")) {
					ECEF_XYZ = Arrays.stream(line.split("\\s+")).limit(3).mapToDouble(x -> Double.parseDouble(x))
							.toArray();

				} else if (line.contains("SYS / # / OBS TYPES")) {
					String[] types_arr = line.replaceAll("SYS / # / OBS TYPES", "").split("\\s+");

					HashMap<String, Integer> type_index = new HashMap<String, Integer>();
					HashSet<String> avail = new HashSet<String>();
					for (int i = 0; i < Integer.parseInt(types_arr[1]); i++) {
						String code = types_arr[i + 2].trim();
						type_index.put(code, i);
						if (code.charAt(0) == 'C') {
							avail.add(code.substring(1));
						}

					}
					char SSI = types_arr[0].trim().charAt(0);
					type_index_map.put(SSI, type_index);
					availObs.put(SSI, avail);

				}
			}
			if (useSNX) {
				String snxPath = "C:\\Users\\Naman\\Desktop\\rinex_parse_files\\input_files\\complementary\\igs20P21004.ssc\\igs20P21004.ssc";
				ECEF_XYZ = SINEX.sinex_process(snxPath, siteCode);
			}

			String[] obsv_msgs = input.next().trim().split(">");
			for (String msg : obsv_msgs) {
				if (msg.isBlank()) {
					continue;
				}

				HashMap<Character, HashMap<Integer, HashMap<Character, ArrayList<Observable>>>> SV = new HashMap<Character, HashMap<Integer, HashMap<Character, ArrayList<Observable>>>>();
				ObservationMsg Msg = new ObservationMsg();
				msg = msg.trim();

				String[] msgLines = msg.split("\\R+");
				for (int i = 1; i < msgLines.length; i++) {
					String msgLine = msgLines[i];
					String SVID = msgLine.substring(0, 3);
					char SSI = SVID.charAt(0);// Satellite System Indentifier
					int obsSize = type_index_map.get(SSI).size();
					String[] obsvs = new String[obsSize];
					msgLine = msgLine.substring(3);
					String[] tokens = msgLine.split("(?<=\\G.{16})");

					for (int j = 0; j < tokens.length; j++) {
						String token = tokens[j];
						token = token.trim();
						if (token.isBlank()) {
							obsvs[j] = null;
						} else {
							obsvs[j] = token.split("\\s+")[0].trim();

						}

					}

					HashMap<String, Integer> type_index = type_index_map.get(SSI);
					for (String str : availObs.get(SSI)) {

						String pseudorange = type_index.containsKey('C' + str) ? obsvs[type_index.get('C' + str)]
								: null;
						if (pseudorange == null) {
							continue;
						}
						String CNo = type_index.containsKey('S' + str) ? obsvs[type_index.get('S' + str)] : null;
						String doppler = type_index.containsKey('D' + str) ? obsvs[type_index.get('D' + str)] : null;
						String phase = type_index.containsKey('L' + str) ? obsvs[type_index.get('L' + str)] : null;
						int freqID = Integer.parseInt(str.charAt(0) + "");
						char codeID = str.charAt(1);
						String frequency = Constellation.frequency.get(SSI).get(freqID) + "";
						SV.computeIfAbsent(SSI, k -> new HashMap<Integer, HashMap<Character, ArrayList<Observable>>>())
								.computeIfAbsent(freqID, k -> new HashMap<Character, ArrayList<Observable>>())
								.computeIfAbsent(codeID, k -> new ArrayList<Observable>())
								.add(new Observable(SVID, pseudorange, CNo, doppler, phase, frequency));

					}

				}

				Msg.set_ECEF_XYZ(ECEF_XYZ);

				Msg.set_RxTime(msgLines[0].trim().split("\\s+"));
				Msg.setObsvSat(SV);
				ObsvMsgs.add(Msg);

			}
			input.close();

		} catch (

		FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return ObsvMsgs;
	}

}
