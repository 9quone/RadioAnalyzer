import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.io.FileOutputStream;
import java.io.IOException;

// Apache POI API for writing to Excel
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Class to extract and analyze the Linearizer and LNA Offset data from the Condensed Logs.
 * @author abhi
 */
public class Analyze {
	public static void main(String[] args) throws FileNotFoundException, IOException {
		long startTime = System.currentTimeMillis();
		
		final File inputDirectory = new File(args[0]); // Directory with all the .xml Log files
		File[] logs = inputDirectory.listFiles();
		
		/**
		 * This Exception is thrown if the input directory is empty.
		 * @author abhi
		 */
		class EmptyInputDirectoryException extends Exception {
			private static final long serialVersionUID = -6120559721117666050L; // ???
			public EmptyInputDirectoryException() {}
		}
		
		// Empty Input Directory exception handling
		try {
			if (logs == null) {
				throw new EmptyInputDirectoryException();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		LogCondenser parse = new LogCondenser(logs);
		String[][] cFiles = parse.condense(); // Extract pertinent sections of the log files

		String[] bands = {"B2", "B4", "B5", "B12", "B13", "B17"}; // ESC LTE bands

		// Tx Linearizer Sweep Max/Min PA State 3/0
		double[][] txSweepMax3 = new double[bands.length][logs.length];
		double[][] txSweepMax0 = new double[bands.length][logs.length];
		double[][] txSweepMin3 = new double[bands.length][logs.length];
		double[][] txSweepMin0 = new double[bands.length][logs.length];

		// APT Tx Linearizer Sweep Max/Min PA State 3/0
		double[][] aptTxSweepMax3 = new double[bands.length][logs.length];
		double[][] aptTxSweepMax0 = new double[bands.length][logs.length];
		double[][] aptTxSweepMin3 = new double[bands.length][logs.length];
		double[][] aptTxSweepMin0 = new double[bands.length][logs.length];

		for (int k = 0; k < bands.length; k++) {
			double[][] maxPowers = new double[logs.length][]; // [Tx Max PA State 3, Tx Max PA State 0, APT Tx Max PA State 3, APT Tx Max PA State 0] for all logs
			double[][] minPowers = new double[logs.length][]; // [Tx Min PA State 3, Tx Min PA State 0, APT Tx Min PA State 3, APT Tx Min PA State 0] for all logs

			int indexMax = 0;
			int indexMin = 0;

			for (int i = 0; i < cFiles[k].length; i++) {
				maxPowers[indexMax] = extractPower(cFiles[k][i], "Tx Lin Swp Max Power"); // Extract Tx Lin Swp Max Power from .xml Log file
				indexMax++;
				minPowers[indexMin] = extractPower(cFiles[k][i], "Tx Lin Swp Min Power"); // Extract Tx Lin Swp Min Power from .xml Log file
				indexMin++;
			}
			
			// Switching from [log][band] format to [band][log] format
			for (int i = 0; i < maxPowers.length; i++) {
				txSweepMax3[k][i] = maxPowers[i][0];
				txSweepMax0[k][i] = maxPowers[i][1];
				aptTxSweepMax3[k][i] = maxPowers[i][2];
				aptTxSweepMax0[k][i] = maxPowers[i][3];
			}

			for (int i = 0; i < minPowers.length; i++) {
				txSweepMin3[k][i] = minPowers[i][0];
				txSweepMin0[k][i] = minPowers[i][1];
				aptTxSweepMin3[k][i] = minPowers[i][2];
				aptTxSweepMin0[k][i] = minPowers[i][3];
			}
		}
		
		int[] rxLvls = {-61, -60, -50, -40, -40, -40};
		int[] devices = {0, 2, 1, 3};
		// Target channels for LNA Offset data are in the middle of each bandwidth
		String[] targetChannels = { "18900", "20190", "20512", "23100", "23220", "23779", "20512", "23100", "23220", "23779" };
		
		int pos = 0;
		
		final int lnaSets = 10; // 1 set of LNA Offset data for the 10 LTE bands ( 6 regular: B2,B4,B5,B12,B13,B17 / 4 diversity: B5,B12,B13,B17)
		int[][] allLna = new int[lnaSets * logs.length][]; // Stores the 10 sets of LNA data for every log
		boolean[] isCorrupt = new boolean[logs.length]; // Used to track which logs have improper formatting of LNA Offset data
		
		for (int i = 0; i < cFiles.length; i++) {
			for (int j = 0; j < cFiles[i].length; j++) {
				ArrayList<Integer> logLna = new ArrayList<Integer>(); // Using an array list because LTE B2 and B2 have more LNA Offset data than the other bands
				int[] lnaOffset;
				
				try {
					 logLna = getRxFCompLNAOffset(cFiles[i][j], targetChannels[i]);
					 
					 // Convert from ArrayList to Array
					 lnaOffset = new int[logLna.size()];
					 for (int k = 0; k < lnaOffset.length; k++) {
						 lnaOffset[k] = logLna.get(k);
					 }
					 
				} catch (StringIndexOutOfBoundsException e) {
					System.out.println("Error in Parsing LNA of " + logs[j].getName());
					isCorrupt[j] = true; // Marks log as corrupt
					lnaOffset = new int[24]; // Sets LNA Offset values to 0
				}
				
				allLna[pos] = lnaOffset;
				pos++;
			}
		}
		
		// Counts the total number of corrupt logs
		int corruptLogs = 0;
		for (boolean b : isCorrupt) {
			if (b) {
				corruptLogs++;
			}
		}
		
		int correctedLogsLength = logs.length - corruptLogs; // Correct total number of logs to be used in statistical analysis
		
		// Prepares B2 LNA Offset data for output
		int[][] b2lna = new int[4][];
		for (int k = 0; k < 4; k++) { // Iterating across 4 devices for B2 LNA Offset data
			int[] temp = new int[6];
			for (int i = 0; i < 6; i++) { // Iterating across the 6 RxLevels (-61, -60, -50, -40, -40, -40)
				int lnaSum = 0;
				for (int j = 0; j < logs.length; j++) {
					if (!isCorrupt[j]) {
						lnaSum += allLna[j][i + k * 6]; // Add up the LNA data for each device, for each RxLevel, for each log.
					}
				}
				temp[i] = lnaSum / correctedLogsLength; // Divides by total logs used to get average LNA Offset
			}
			b2lna[k] = temp;
		}
		
		// Prepares B4 LNA Offset data for output (Similar to B2)
		int[][] b4lna = new int[4][];
		for (int k = 0; k < 4; k++) {
			int[] temp = new int[6];
			for (int i = 0; i < 6; i++) {
				int lnaSum = 0;
				for (int j = logs.length; j < logs.length * 2; j++) {
					if (!isCorrupt[j - logs.length]) {
						lnaSum += allLna[j][i + k * 6];
					}
				}
				temp[i] = lnaSum / correctedLogsLength;
			}
			b4lna[k] = temp;
		}
		
		// Prepares B5-17 and B5-17 Diversity LNA Offset data for output
		int[][] nonPRXlna = new int[8][];
		for (int k = 0; k < nonPRXlna.length; k++) { // Iterates across the remaining LTE bands
			int[] currentBandlna = new int[6];
			for (int i = 0; i < 6; i++) { // Iterating across the 6 RxLevels (-61, -60, -50, -40, -40, -40)
				int lnaSum = 0;
				for (int j = logs.length * (k + 2); j < logs.length * (k + 3); j++) {
					if (!isCorrupt[j - logs.length * (k + 2)]) {
						lnaSum += allLna[j][i]; // Add up the LNA data for each band, for each RxLevel, for each log.
					}
				}
				currentBandlna[i] = lnaSum / correctedLogsLength;
			}
			nonPRXlna[k] = currentBandlna;
		}
		
		// Initializing Excel workbook
		int rowCount = 0;
		int colCount = 0;
		XSSFWorkbook workbook = new XSSFWorkbook();
		XSSFSheet sheet = workbook.createSheet();
		
		String[] stats = {"Min", "Max", "Mean", "Median", "Std. Dev."};
		
		// Cell formatting
		CellStyle style = workbook.createCellStyle();
		style.setAlignment(HorizontalAlignment.LEFT);
		
		CellStyle center = workbook.createCellStyle();
		center.setAlignment(HorizontalAlignment.CENTER);
		
		// Outputting data to Excel
		for (int i = 0; i < bands.length; i++) {
			XSSFRow row = sheet.createRow(rowCount);
			row.createCell(colCount).setCellValue("ESC LTE " + bands[i]);
			colCount = 2;
			
			// Creates the [Min, Max, Mean, Median, Std. Dev.] column headers
			for (; colCount < stats.length + 2; colCount++) {
				XSSFCell cell = row.createCell(colCount);
				cell.setCellStyle(style);
				cell.setCellValue(stats[colCount - 2]);
			}
			colCount = 0;
			rowCount++;
			
			//----------------------------------------------------------------
			// Tx Linearizer Sweep Max Data
			row = sheet.createRow(rowCount);
			row.createCell(colCount).setCellValue("Tx Linearizer Sweep Max");
			rowCount++;
			row = sheet.createRow(rowCount);
			row.createCell(colCount).setCellValue("PA State 3: Power");
			colCount = 2;
			double[] dataMax3 = getStats(txSweepMax3[i]);
			for (double d : dataMax3) {
				XSSFCell cell = row.createCell(colCount);
				cell.setCellStyle(style);
				cell.setCellValue(d);
				colCount++;
			}
			rowCount++;
			colCount = 0;
			
			row = sheet.createRow(rowCount);
			row.createCell(colCount).setCellValue("PA State 0: Power");
			colCount = 2;
			double[] dataMax0 = getStats(txSweepMax0[i]);
			for (double d : dataMax0) {
				XSSFCell cell = row.createCell(colCount);
				cell.setCellStyle(style);
				cell.setCellValue(d);
				colCount++;
			}
			rowCount += 2;
			colCount = 0;
			
			//----------------------------------------------------------------
			// Tx Linearizer Sweep Min Data
			row = sheet.createRow(rowCount);
			row.createCell(colCount).setCellValue("Tx Linearizer Sweep Min");
			rowCount++;
			row = sheet.createRow(rowCount);
			row.createCell(colCount).setCellValue("PA State 3: Power");
			colCount = 2;
			double[] dataMin3 = getStats(txSweepMin3[i]);
			for (double d : dataMin3) {
				XSSFCell cell = row.createCell(colCount);
				cell.setCellStyle(style);
				cell.setCellValue(d);
				colCount++;	
			}
			rowCount++;
			colCount = 0;
			
			row = sheet.createRow(rowCount);
			row.createCell(colCount).setCellValue("PA State 0: Power");
			colCount = 2;
			double[] dataMin0 = getStats(txSweepMin0[i]);
			for (double d : dataMin0) {
				XSSFCell cell = row.createCell(colCount);
				cell.setCellStyle(style);
				cell.setCellValue(d);
				colCount++;	
			}
			rowCount += 2;
			colCount = 0;
			
			//----------------------------------------------------------------
			// APT Tx Linearizer Sweep Max Data
			row = sheet.createRow(rowCount);
			row.createCell(colCount).setCellValue("APT Tx Linearizer Sweep Max");
			rowCount++;
			row = sheet.createRow(rowCount);
			row.createCell(colCount).setCellValue("PA State 3: Power");
			colCount = 2;
			double[] aptdataMax3 = getStats(aptTxSweepMax3[i]);
			for (double d : aptdataMax3) {
				XSSFCell cell = row.createCell(colCount);
				cell.setCellStyle(style);
				cell.setCellValue(d);
				colCount++;	
			}
			rowCount++;
			colCount = 0;
			
			row = sheet.createRow(rowCount);
			row.createCell(colCount).setCellValue("PA State 0: Power");
			colCount = 2;
			double[] aptdataMax0 = getStats(aptTxSweepMax0[i]);
			for (double d : aptdataMax0) {
				XSSFCell cell = row.createCell(colCount);
				cell.setCellStyle(style);
				cell.setCellValue(d);
				colCount++;	
			}
			rowCount += 2;
			colCount = 0;
			
			//----------------------------------------------------------------
			// APT Tx Linearizer Sweep Min Data
			row = sheet.createRow(rowCount);
			row.createCell(colCount).setCellValue("APT Tx Linearizer Sweep Min");
			rowCount++;
			row = sheet.createRow(rowCount);
			row.createCell(colCount).setCellValue("PA State 3: Power");
			colCount = 2;
			double[] aptdataMin3 = getStats(aptTxSweepMin3[i]);
			for (double d : aptdataMin3) {
				XSSFCell cell = row.createCell(colCount);
				cell.setCellStyle(style);
				cell.setCellValue(d);
				colCount++;	
			}
			rowCount++;
			colCount = 0;
			
			row = sheet.createRow(rowCount);
			row.createCell(colCount).setCellValue("PA State 0: Power");
			colCount = 2;
			double[] aptdataMin0 = getStats(aptTxSweepMin0[i]);
			for (double d : aptdataMin0) {
				XSSFCell cell = row.createCell(colCount);
				cell.setCellStyle(style);
				cell.setCellValue(d);
				colCount++;	
			}
			rowCount += 2;
			colCount = 0;
			
			//----------------------------------------------------------------
			// Outputting LNA Offset Data
			row = sheet.createRow(rowCount);
			row.createCell(colCount).setCellValue("LNA Offset Freq Comp");
			rowCount++;
			
			// LTE B2
			if (i == 0) {
				for (int j = 0; j < b2lna.length; j++) {
					row = sheet.createRow(rowCount);
					row.createCell(colCount).setCellValue("RxFreqCompLNAOffset");
					colCount++;
					XSSFCell cell = row.createCell(colCount);
					cell.setCellStyle(center);
					cell.setCellValue("Dev" + devices[j]);
					rowCount++;
					colCount = 0;
					
					for (int e = 0; e < b2lna[j].length; e++) {
						row = sheet.createRow(rowCount);
						row.createCell(colCount).setCellValue("RxLvl " + rxLvls[e]);
						colCount++;
						row.createCell(colCount).setCellValue(b2lna[j][e]);
						colCount = 0;
						rowCount ++;
					}
					rowCount++;
				}
				rowCount ++;
			}
			// LTE B4
			else if (i == 1) {
				for (int j = 0; j < b4lna.length; j++) {
					row = sheet.createRow(rowCount);
					row.createCell(colCount).setCellValue("RxFreqCompLNAOffset");
					colCount++;
					XSSFCell cell = row.createCell(colCount);
					cell.setCellStyle(center);
					cell.setCellValue("Dev" + devices[j]);
					rowCount++;
					colCount = 0;
					
					for (int e = 0; e < b4lna[j].length; e++) {
						row = sheet.createRow(rowCount);
						row.createCell(colCount).setCellValue("RxLvl " + rxLvls[e]);
						colCount++;
						row.createCell(colCount).setCellValue(b4lna[j][e]);
						colCount = 0;
						rowCount ++;
					}
					rowCount++;
				}
				rowCount++;
			}
			// LTE B5-17
			else {
				row = sheet.createRow(rowCount);
				row.createCell(colCount).setCellValue("RxFreqCompLNAOffset");
				colCount++;
				XSSFCell cell = row.createCell(colCount);
				cell.setCellStyle(center);
				cell.setCellValue("Dev0");
				rowCount++;
				colCount = 0;
				
				for (int e = 0; e < nonPRXlna[i - 2].length; e++) {
					row = sheet.createRow(rowCount);
					row.createCell(colCount).setCellValue("RxLvl " + rxLvls[e]);
					colCount++;
					row.createCell(colCount).setCellValue(nonPRXlna[i - 2][e]);
					colCount = 0;
					rowCount++;
				}
				rowCount += 2;
			}
		}
		
		String[] dbands = {"B5 Diversity", "B12 Diversity", "B13 Diversity", "B17 Diversity"};
		
		// Outputting LNA Offset for LTE B5-B17 Diversity
		for (int i = 0; i < dbands.length; i++) {
			XSSFRow row = sheet.createRow(rowCount);
			row.createCell(colCount).setCellValue("ESC LTE " + dbands[i]);
			rowCount++;
			row = sheet.createRow(rowCount);
			row.createCell(colCount).setCellValue("LNA Offset Freq Comp");
			rowCount++;
			row = sheet.createRow(rowCount);
			row.createCell(colCount).setCellValue("RxFreqCompLNAOffset");
			colCount++;
			XSSFCell cell = row.createCell(colCount);
			cell.setCellStyle(center);
			cell.setCellValue("Dev1");
			rowCount++;
			colCount = 0;
			
			for (int e = 0; e < nonPRXlna[i + 4].length; e++) {
				row = sheet.createRow(rowCount);
				row.createCell(colCount).setCellValue("RxLvl " + rxLvls[e]);
				colCount++;
				row.createCell(colCount).setCellValue(nonPRXlna[i + 4][e]); // [i + 4] because first 4 arrays are for regular LTE B5-B17
				colCount = 0;
				rowCount++;
			}
			rowCount += 2;
		}
		
		// Excel formatting
		sheet.setColumnWidth(0, 7500);
		sheet.setColumnWidth(1, 2500);
		for (int col = 2; col < 7; col++)
			sheet.setColumnWidth(col, 2500);
		
		// Creates output Excel file in same directory containing the log files
		workbook.write(new FileOutputStream(inputDirectory.getPath() + "\\Organized Data.xlsx"));
		workbook.close();
		
		long endTime = System.currentTimeMillis();
		long totalTime = endTime - startTime;
		System.out.println();
		System.out.println("Success! Completed in " + totalTime/1000 + " seconds.");
		System.out.println(logs.length + " logs processed.");
	}
	
	/**
	 * Extracts the (APT) Tx Linearizer data
	 * 
	 * @param str: ESC LTE band data from a single log
	 * @param key: Max or Min Power
	 * @return double[] with the Tx and APT Tx Linearizer data for PA State 3 and PA State 0.
	 */
	public static double[] extractPower(String str, String key) {
		double[] values = new double[4]; // {Tx PA State 3, Tx PA State 0, APT Tx PA State 3, APT Tx PA State 0}
		int addPos = 0;
		int index = str.indexOf(key);
		while (index >= 0) {
			String s = str.substring(index, index + 40);
			String target = s.substring(s.indexOf("<V>") + 3, s.indexOf("</V>")); // Extracts the value
			values[addPos] = Double.parseDouble(target);
			addPos++;
			index = str.indexOf(key, index + 1); // Proceeds to next value 
		}
		return values;
	}
	
	/**
	 * Returns the output of statistical analysis on the values of nums.
	 */
	public static double[] getStats(double[] nums) {
		double[] result = new double[5];
		result[0] = findMin(nums);
		result[1] = findMax(nums);
		result[2] = findMean(nums);
		result[3] = findMedian(nums);
		result[4] = findStdDev(nums);
		return result;
	}
	
	/**
	 * Calculates the min value of nums.
	 */
	public static double findMin(double[] nums) {
		double min = nums[0];
		for (int i = 1; i < nums.length; i++) {
			if (nums[i] < min) {
				min = nums[i];
			}
		}
		return min;
	}
	
	/**
	 * Calculates the max value of nums.
	 */
	public static double findMax(double[] nums) {
		double max = nums[0];
		for (int i = 1; i < nums.length; i++) {
			if (nums[i] > max) {
				max = nums[i];
			}
		}
		return max;
	}
	
	/**
	 * Calculates the mean value of nums (double).
	 */
	public static double findMean(double[] nums) {
		double sum = 0;
		for (double value : nums) {
			sum += value;
		}
		return round(sum / nums.length, 2);
	}
	
	/**
	 * Calculates the mean value of nums (int).
	 */
	public static double findMean(int[] nums) {
		double sum = 0;
		for (int value : nums) {
			sum += value;
		}
		return sum / nums.length;
	}
	
	/**
	 * Calculates the median value of nums.
	 */
	public static double findMedian(double[] nums) {
		Arrays.sort(nums);
		double median;
		if (nums.length % 2 == 0) {
			int over = nums.length / 2;
			int under = nums.length / 2 - 1;
			median = (nums[under] + nums[over]) / 2;
		} else {
			median = nums[nums.length / 2];
		}
		return median;
	}
	
	/**
	 * Calculates the standard deviation of the values in nums.
	 */
	public static double findStdDev(double[] nums) {
		double mean = findMean(nums);
		double sum = 0;
		for (double value : nums) {
			sum += Math.pow(Math.abs(value - mean), 2);
		}
		return round(Math.sqrt(sum / nums.length), 2);
	}
	
	/**
	 * Rounds a double to "toDec" places after the decimal point.
	 */
	private static double round(double num, int toDec) {
		double factor = Math.pow(10.0, toDec);
		num = Math.round(num * factor) / factor;
		return num;
	}
	
	/**
	 * Extracts RxFCompLNAOffset values
	 * 
	 * @param str: ESC LTE band data from a single log
	 * @param channel: The particular channel to get LNA Offset data for
	 * @return ArrayList<Integer> with the RxFCompLNAOffset values for the specified channel across all RxLevels
	 */
	public static ArrayList<Integer> getRxFCompLNAOffset(String str, String channel) {
		ArrayList<Integer> values = new ArrayList<Integer>();

		str = str.substring(str.indexOf("LNA")); // Find the LNA data section
		int index = str.indexOf(channel);

		while (index >= 0) {
			String s = str.substring(index, str.indexOf("Channel", index));
			int pos = s.indexOf("RxFCompLNAOffset"); // Find the section with the RxFCompLNAOffset values
			s = s.substring(pos, pos + 40);

			String target = s.substring(s.indexOf("<V>") + 3, s.indexOf("</V>")); // Extracts the value
			values.add(Integer.parseInt(target));
			index = str.indexOf(channel, index + 1); // Move to next RxLevel
		}
		return values;
	}
}