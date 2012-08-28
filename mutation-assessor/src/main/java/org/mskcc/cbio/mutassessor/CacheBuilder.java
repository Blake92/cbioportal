package org.mskcc.cbio.mutassessor;

import java.io.*;
import java.sql.SQLException;
import java.util.HashMap;

/**
 * Utility class to process MA files and create cache build on the same key structure
 * as the oncotator cache.
 */
public class CacheBuilder
{
	public static final String MA_VARIANT = "mutation";
	public static final String MA_FIMPACT = "func. impact";
	public static final String MA_PROTEIN_CHANGE = "uniprot variant";
	public static final String MA_LINK_MSA = "msa"; // TODO no header in the files yet
	public static final String MA_LINK_PDB = "pdb"; // TODO no header in the files yet

	protected HashMap<String, Integer> headerIndices;

	public CacheBuilder()
	{

	}

	/**
	 * Processes all files in a given directory (assuming that all files are MA files).
	 *
	 * @param inputDirectory    target directory containing input files
	 * @throws IOException
	 * @throws SQLException
	 */
	public void processDirectory(File inputDirectory) throws IOException, SQLException
	{
		if (inputDirectory.isDirectory())
		{
			for (File file : inputDirectory.listFiles())
			{
				if (!file.isDirectory())
				{
					this.processFile(file, new File(file.getName() + ".sql"));
				}
			}
		}
	}

	/**
	 * Processes a single MA file, and inserts a row to DB for each line.
	 *
	 * @param inputMA       input MA file to process
	 * @param outputSql     output SQL file to create
	 * @throws IOException
	 * @throws SQLException
	 */
	public void processFile(File inputMA, File outputSql) throws IOException, SQLException
	{
		DaoMutAssessorCache dao = DaoMutAssessorCache.getInstance();

		BufferedReader reader = new BufferedReader(new FileReader(inputMA));
		BufferedWriter writer = new BufferedWriter(new FileWriter(outputSql));

		// process header line
		String line = reader.readLine();
		this.headerIndices = this.buildIndexMap(line);

		// process each data line
		while ((line = reader.readLine()) != null)
		{
			// skip empty lines
			if (line.trim().length() == 0)
			{
				continue;
			}

			MutationAssessorRecord record = this.parseDataLine(line);

			if (record != null)
			{
				//dao.put(record);
				// creating an SQL script file, instead of using slower JDBC...
				writer.write(dao.getInsertSql(record));
				writer.newLine();
			}
		}

		reader.close();
		writer.close();
	}

	protected HashMap<String, Integer> buildIndexMap(String headerLine)
	{
		HashMap<String, Integer> headerIndices = new HashMap<String, Integer>();

		String[] parts = headerLine.split("\t");

		for (int i = 0; i < parts.length ; i++)
		{
			if (parts[i].equalsIgnoreCase(MA_VARIANT))
			{
				headerIndices.put(MA_VARIANT, i);
			}
			else if (parts[i].equalsIgnoreCase(MA_FIMPACT))
			{
				headerIndices.put(MA_FIMPACT, i);
			}
			else if (parts[i].equalsIgnoreCase(MA_PROTEIN_CHANGE))
			{
				headerIndices.put(MA_PROTEIN_CHANGE, i);
			}
			else if (parts[i].equalsIgnoreCase(MA_LINK_MSA))
			{
				headerIndices.put(MA_LINK_MSA, i);
			}
			else if (parts[i].equalsIgnoreCase(MA_LINK_PDB))
			{
				headerIndices.put(MA_LINK_PDB, i);
			}
		}

		return headerIndices;
	}

	protected MutationAssessorRecord parseDataLine(String dataLine)
	{
		String[] parts = dataLine.split("\t", -1);

		String mutation = this.getPartString(this.getHeaderIndex(MA_VARIANT), parts);
		String key = this.generateKey(mutation);
		String impact = this.getPartString(this.getHeaderIndex(MA_FIMPACT), parts);
		String proteinChange = this.getPartString(this.getHeaderIndex(MA_PROTEIN_CHANGE), parts);
		String structureLink = this.getPartString(this.getHeaderIndex(MA_LINK_PDB), parts);
		String alignmentLink = this.getPartString(this.getHeaderIndex(MA_LINK_MSA), parts);

		MutationAssessorRecord record = new MutationAssessorRecord(key);
		record.setImpact(impact);
		record.setProteinChange(proteinChange);
		record.setStructureLink(structureLink);
		record.setAlignmentLink(alignmentLink);

		return record;
	}

	/**
	 * Assuming the format : [build],[chr],[startPos],[refAllele],[tumAllele]
	 *
	 * @param mutation
	 * @return
	 */
	protected String generateKey(String mutation)
	{
		String[] parts = mutation.split(",");

		String key = parts[1] + "_" + parts[2] + "_" + parts[2] + "_" +
			parts[3] + "_" + parts[4];

		return key;
	}

	protected Integer getHeaderIndex(String header)
	{
		Integer index = this.headerIndices.get(header);

		if (index == null)
		{
			index = -1;
		}

		return index;
	}

	protected String getPartString(Integer index, String[] parts)
	{
		try
		{
			if (parts[index].length() == 0)
			{
				return MutationAssessorRecord.NA_STRING;
			}
			else
			{
				return parts[index];
			}
		}
		catch (ArrayIndexOutOfBoundsException e)
		{
			return MutationAssessorRecord.NA_STRING;
		}
	}
}
