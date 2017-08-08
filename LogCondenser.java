import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

/**
 * Class to extract ESC LTE data from QDART Test Report logs.
 * @author abhi
 */
public class LogCondenser {
	private File[] logs; // All the log files
	private String[][] cFiles; // Extracted ESC LTE data
	
	public LogCondenser(File[] logs) {
		this.logs = logs;
		final int bands = 10; // 6 regular: B2,B4,B5,B12,B13,B17 / 4 diversity: B5,B12,B13,B17
		cFiles = new String[bands][this.logs.length];
	}
	
	/**
	 * Extracts relevant ESC LTE data into a String[][]
	 * @return String[][] of the ESC LTE data of B2,B4,B5,B12,B13,B17 bandwidths across all the provided logs.
	 * @throws FileNotFoundException
	 */
	public String[][] condense() throws FileNotFoundException {
		Scanner in; // To read each log .xml file
		int[] bands = {2,4,5,12,13,17,5}; // Second LTE 5 included to function as stopping point for first LTE B17
		int pos = 0; // Track final index of LTE B2-B17
		
		// Iterates over LTE B2 and B4 along with the first version of LTE B5-B17
		for (int e = 0; e < bands.length - 1; e++) {
			for (int i = 0; i < logs.length; i++) {
				in = new Scanner(logs[i]);
				String lines = in.nextLine();
				in.close();
				
				int start = lines.indexOf("ESC LTE B" + bands[e]); // Start of the ESC LTE Data for each band
				int end = lines.indexOf("ESC LTE B" + bands[e + 1], start); // End of the ESC LTE Data (Start of next LTE band)
				lines = lines.substring(start, end);
				
				cFiles[e][i] = lines;
				pos = e;
			}
		}
		
		// Iterates over LTE B5-B17 Diversity
		int[] bands2 = {5,12,13,17};
		for (int e = 0; e < bands2.length; e++) {
			for (int i = 0; i < logs.length; i++) {
				in = new Scanner(logs[i]);
				String lines = in.nextLine();
				in.close();
				
				int start;
				int end;
				String endSet1 = "ESC LTE B17</ExtendedName><NodeName>ESC LTE B17</NodeName>";
				start = lines.indexOf("ESC LTE B" + bands2[e], lines.indexOf(endSet1) + endSet1.length()); // Only considering B5-17 Diversity
				
				if (e == bands2.length - 1) {
					end = lines.indexOf("Run_RSB_Pcell_Tx_LO_Cal", start); // B17 Diversity is the last LTE band, so using an alternate way of finding the end of the ESC LTE Data 
				} else { 
					end = lines.indexOf("ESC LTE B" + bands2[e + 1], start); // End of the ESC LTE Data
				} 
				
				lines = lines.substring(start, end);
				cFiles[pos + e + 1][i] = lines;
			}
		}
		return cFiles;
	}
}
